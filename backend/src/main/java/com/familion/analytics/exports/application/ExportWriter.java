package com.familion.analytics.exports.application;

import com.familion.analytics.exports.domain.ExportFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface ExportWriter {

    ExportFormat format();

    void writeTransactions(OutputStream out, String lensUnit, TransactionRowSource rowSource)
            throws IOException;

    void writeMonthlySummary(OutputStream out, List<MonthlySummaryRow> rows) throws IOException;

    @FunctionalInterface
    interface TransactionRowSink {
        void accept(ExportTransactionLine line) throws IOException;
    }

    @FunctionalInterface
    interface TransactionRowSource {
        void streamTo(TransactionRowSink sink) throws IOException;
    }
}
