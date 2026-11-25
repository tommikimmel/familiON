package com.familion.analytics.exports.application;

import com.familion.analytics.exports.domain.ExportFormat;
import java.io.IOException;
import java.io.OutputStream;

public interface ExportWriter {

    ExportFormat format();

    void writeTransactions(OutputStream out, String lensUnit, TransactionRowSource rowSource)
            throws IOException;

    @FunctionalInterface
    interface TransactionRowSink {
        void accept(ExportTransactionLine line) throws IOException;
    }

    @FunctionalInterface
    interface TransactionRowSource {
        void streamTo(TransactionRowSink sink) throws IOException;
    }
}
