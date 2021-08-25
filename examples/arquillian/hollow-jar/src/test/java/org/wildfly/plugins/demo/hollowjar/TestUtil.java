package org.wildfly.plugins.demo.hollowjar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestUtil {

    static String performCall(URL url) throws Exception {
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        return processResponse(conn);
    }

    private static String processResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            final InputStream err = conn.getErrorStream();
            try {
                String response = err != null ? read(err) : null;
                throw new IOException(String.format("HTTP Status %d Response: %s", responseCode, response));
            } finally {
                if (err != null) {
                    err.close();
                }
            }
        }
        final InputStream in = conn.getInputStream();
        try {
            return read(in);
        } finally {
            in.close();
        }
    }

    private static String read(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            out.write(b);
        }
        return out.toString();
    }
}
