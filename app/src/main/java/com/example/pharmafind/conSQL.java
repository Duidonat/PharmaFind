package com.example.pharmafind;

import android.annotation.SuppressLint;
import android.os.StrictMode;
import android.util.Log;
import java.sql.Connection;
import java.sql.DriverManager;

public class conSQL {

    Connection con;

    // Your Somee SQL Server credentials:
    String ip = "pharmaFind.mssql.somee.com";
    String port = "1433";
    String db = "pharmaFind";
    String username = "dowedonat_SQLLogin_1";
    String password = "5xte8vpjre";

    @SuppressLint("NewApi")
    public Connection conclass() {
        StrictMode.ThreadPolicy a = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(a);
        String ConnectURL;

        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            ConnectURL = "jdbc:jtds:sqlserver://" + ip + ":" + port + ";databasename=" + db + ";user=" + username + ";password=" + password + ";";
            con = DriverManager.getConnection(ConnectURL);
        } catch (Exception e) {
            Log.e("Error is ", e.getMessage() != null ? e.getMessage() : "Unknown Connection Error");
        }

        return con;
    }
}
