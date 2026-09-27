package com.example.pharmafind;

import android.annotation.SuppressLint;
import android.os.StrictMode;
import android.util.Log;
import java.sql.Connection;
import java.sql.DriverManager;

public class conSQL {

    private final String ip = "pharmaFind.mssql.somee.com";
    private final String port = "1433";
    private final String db = "pharmaFind";
    private final String username = "dowedonat_SQLLogin_1";
    private final String password = "5xte8vpjre";

    @SuppressLint("NewApi")
    public Connection conclass() {
        StrictMode.ThreadPolicy a = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(a);

        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Class.forName("net.sourceforge.jtds.jdbc.Driver");
                DriverManager.setLoginTimeout(12);
                String connectUrl = "jdbc:jtds:sqlserver://" + ip + ":" + port + ";databasename=" + db
                        + ";user=" + username + ";password=" + password
                        + ";loginTimeout=12;socketTimeout=20;";
                Connection con = DriverManager.getConnection(connectUrl);
                if (con != null && !con.isClosed()) {
                    return con;
                }
            } catch (Exception e) {
                Log.e("conSQL", "Connection Attempt " + attempt + " Failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown Error"));
                if (attempt == maxAttempts) {
                    return null;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }
}
