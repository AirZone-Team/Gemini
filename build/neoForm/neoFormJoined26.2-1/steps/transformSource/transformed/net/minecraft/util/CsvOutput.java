package net.minecraft.util;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

public class CsvOutput {
    private static final String LINE_SEPARATOR = "\r\n";
    private static final String FIELD_SEPARATOR = ",";
    private final Writer output;
    private final int columnCount;

    private CsvOutput(Writer output, List<String> headers) throws IOException {
        this.output = output;
        this.columnCount = headers.size();
        this.writeLine(headers.stream());
    }

    public static CsvOutput.Builder builder() {
        return new CsvOutput.Builder();
    }

    public void writeRow(@Nullable Object... values) throws IOException {
        if (values.length != this.columnCount) {
            throw new IllegalArgumentException("Invalid number of columns, expected " + this.columnCount + ", but got " + values.length);
        }

        this.writeLine(Stream.of(values));
    }

    private void writeLine(Stream<? extends @Nullable Object> values) throws IOException {
        this.output.write(values.map(CsvOutput::getStringValue).collect(Collectors.joining(",")) + "\r\n");
    }

    private static String getStringValue(@Nullable Object value) {
        return StringEscapeUtils.escapeCsv(value != null ? value.toString() : "[null]");
    }

    public static class Builder {
        private final List<String> headers = Lists.newArrayList();

        public CsvOutput.Builder addColumn(String header) {
            this.headers.add(header);
            return this;
        }

        public CsvOutput build(Writer writer) throws IOException {
            return new CsvOutput(writer, this.headers);
        }
    }
}
