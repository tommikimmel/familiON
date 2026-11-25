package com.familion.receipts.infrastructure.ocr;

import com.familion.receipts.application.port.ReceiptDraft;
import com.familion.receipts.application.port.ReceiptParserPort;
import com.familion.receipts.domain.ReceiptMediaType;
import com.familion.receipts.domain.ReceiptParsingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TesseractCliReceiptParser implements ReceiptParserPort {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_STDERR_LENGTH = 300;

    private final String tesseractBinary;
    private final String pdftoppmBinary;
    private final boolean pdftoppmAvailable;
    private final String languageArg;
    private final Duration timeout;
    private final HeuristicTicketTextParser textParser;

    public TesseractCliReceiptParser(
            String tesseractBinary,
            Duration timeout,
            boolean pdftoppmAvailable,
            HeuristicTicketTextParser textParser) {
        this(
                tesseractBinary,
                "pdftoppm",
                pdftoppmAvailable,
                detectLanguageArg(tesseractBinary),
                timeout,
                textParser);
    }

    TesseractCliReceiptParser(
            String tesseractBinary,
            String pdftoppmBinary,
            boolean pdftoppmAvailable,
            String languageArg,
            Duration timeout,
            HeuristicTicketTextParser textParser) {
        this.tesseractBinary = Objects.requireNonNull(tesseractBinary, "tesseractBinary must not be null");
        this.pdftoppmBinary = Objects.requireNonNull(pdftoppmBinary, "pdftoppmBinary must not be null");
        this.pdftoppmAvailable = pdftoppmAvailable;
        this.languageArg = Objects.requireNonNull(languageArg, "languageArg must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.textParser = Objects.requireNonNull(textParser, "textParser must not be null");
    }

    public static boolean isBinaryAvailable(String binary) {
        return probeCommand(List.of(binary, "--version"));
    }

    public static boolean isPdftoppmAvailable() {
        return probeCommand(List.of("pdftoppm", "-v"));
    }

    static String detectLanguageArg(String tesseractBinary) {
        try {
            Process process = new ProcessBuilder(tesseractBinary, "--list-langs").start();
            boolean finished = process.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "spa+eng";
            }
            if (process.exitValue() != 0) {
                return "spa+eng";
            }
            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Set<String> langs = new HashSet<>();
            output.lines().map(String::trim).forEach(langs::add);
            boolean hasSpa = langs.contains("spa");
            boolean hasEng = langs.contains("eng");
            if (hasSpa && hasEng) {
                return "spa+eng";
            }
            if (hasSpa) {
                return "spa";
            }
            if (hasEng) {
                return "eng";
            }
            return "spa+eng";
        } catch (IOException ex) {
            return "spa+eng";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "spa+eng";
        }
    }

    @Override
    public ReceiptDraft parse(byte[] content, String mimeType) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(mimeType, "mimeType must not be null");

        byte[] imageBytes = content;
        if (ReceiptMediaType.PDF.mimeType().equalsIgnoreCase(mimeType)) {
            if (!pdftoppmAvailable) {
                throw new ReceiptParsingException(
                        "PDF OCR is not supported by TesseractCliReceiptParser without pdftoppm");
            }
            imageBytes = renderPdfFirstPageToPng(content);
        }

        String rawText = runTesseract(imageBytes);
        return textParser.parse(rawText);
    }

    private String runTesseract(byte[] imageBytes) {
        ProcessResult result = runProcess(
                List.of(tesseractBinary, "stdin", "stdout", "-l", languageArg), imageBytes, "Tesseract OCR");
        return new String(result.stdout(), StandardCharsets.UTF_8);
    }

    private byte[] renderPdfFirstPageToPng(byte[] pdfBytes) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("familion-pdf-ocr-");
            Path inputPdf = tempDir.resolve("input.pdf");
            Path outputPrefix = tempDir.resolve("page");
            Files.write(inputPdf, pdfBytes);

            runProcess(
                    List.of(
                            pdftoppmBinary,
                            "-png",
                            "-f",
                            "1",
                            "-l",
                            "1",
                            "-singlefile",
                            inputPdf.toString(),
                            outputPrefix.toString()),
                    null,
                    "pdftoppm");

            Path renderedPng = tempDir.resolve("page.png");
            if (!Files.isRegularFile(renderedPng)) {
                throw new ReceiptParsingException("pdftoppm did not produce a PNG page for PDF");
            }
            return Files.readAllBytes(renderedPng);
        } catch (IOException ex) {
            throw new ReceiptParsingException("Failed to render PDF for OCR", ex);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private ProcessResult runProcess(List<String> command, byte[] stdinBytes, String label) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException ex) {
            throw new ReceiptParsingException("Failed to start " + label + " process", ex);
        }

        try (ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> stdoutFuture =
                    ioExecutor.submit(() -> readAll(process.getInputStream()));
            Future<byte[]> stderrFuture =
                    ioExecutor.submit(() -> readAll(process.getErrorStream()));
            Future<?> stdinFuture = ioExecutor.submit(() -> writeAndClose(process.getOutputStream(), stdinBytes));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ReceiptParsingException(
                        label + " timed out after " + timeout.toSeconds() + "s");
            }

            awaitQuietly(stdinFuture);
            byte[] stdout = awaitBytes(stdoutFuture, label);
            byte[] stderr = awaitBytes(stderrFuture, label);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errText = summarizeStderr(stderr);
                throw new ReceiptParsingException(
                        label + " failed with exit code " + exitCode
                                + (errText.isEmpty() ? "" : ": " + errText));
            }
            return new ProcessResult(stdout, stderr);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ReceiptParsingException(label + " was interrupted", ex);
        }
    }

    private static boolean probeCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream in = input) {
            return in.readAllBytes();
        }
    }

    private static void writeAndClose(OutputStream output, byte[] content) {
        try (OutputStream out = output) {
            if (content != null && content.length > 0) {
                out.write(content);
                out.flush();
            }
        } catch (IOException ignored) {
            // Process may exit early before consuming all stdin; exit code check will report failure.
        }
    }

    private static void awaitQuietly(Future<?> future) {
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            future.cancel(true);
        }
    }

    private static byte[] awaitBytes(Future<byte[]> future, String label)
            throws InterruptedException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            throw new ReceiptParsingException("Failed reading output from " + label, ex.getCause());
        } catch (Exception ex) {
            future.cancel(true);
            throw new ReceiptParsingException("Timed out reading output from " + label, ex);
        }
    }

    private static String summarizeStderr(byte[] stderr) {
        if (stderr == null || stderr.length == 0) {
            return "";
        }
        String text = new String(stderr, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return text.length() > MAX_STDERR_LENGTH ? text.substring(0, MAX_STDERR_LENGTH) : text;
    }

    private static void deleteDirectoryQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup of temp directory.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup of temp directory.
        }
    }

    private record ProcessResult(byte[] stdout, byte[] stderr) {}
}
