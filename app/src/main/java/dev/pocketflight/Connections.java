package dev.pocketflight;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.util.Set;

public final class Connections {
    public record Wifi(FlightPolicy.ExceptionState state, String description, String ssid) {}
    public static boolean granted(Context c, String permission) {
        return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
    public static String normalizeSsid(String value) {
        if (value == null || value.equals(WifiManager.UNKNOWN_SSID)) return "";
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
            return value.substring(1, value.length() - 1);
        return value;
    }

    @SuppressWarnings("deprecation")
    public static Wifi wifi(Context context, Set<String> exemptions, boolean background) {
        try {
            WifiManager manager = context.getApplicationContext().getSystemService(WifiManager.class);
            if (manager == null || manager.getWifiState() == WifiManager.WIFI_STATE_DISABLED)
                return new Wifi(FlightPolicy.ExceptionState.CLEAR, "Wi-Fi off", "");
            if (manager.getWifiState() != WifiManager.WIFI_STATE_ENABLED)
                return new Wifi(exemptions.isEmpty() ? FlightPolicy.ExceptionState.CLEAR : FlightPolicy.ExceptionState.UNKNOWN,
                        "Wi-Fi is changing state", "");
            ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
            boolean connected = false;
            for (Network network : connectivity.getAllNetworks()) {
                NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
                // Includes Wi-Fi without internet, even when mobile data or a VPN is the default.
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) connected = true;
            }
            if (!connected) return new Wifi(FlightPolicy.ExceptionState.CLEAR, "No Wi-Fi connection", "");
            boolean access = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    && (!background || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    && context.getSystemService(LocationManager.class).isLocationEnabled();
            WifiInfo info = access ? manager.getConnectionInfo() : null;
            String ssid = info == null ? "" : normalizeSsid(info.getSSID());
            if (ssid.isEmpty()) return new Wifi(exemptions.isEmpty() ? FlightPolicy.ExceptionState.CLEAR : FlightPolicy.ExceptionState.UNKNOWN,
                    "Wi-Fi name unavailable — allow precise location all the time and enable Location", "");
            boolean match = exemptions.contains(ssid);
            return new Wifi(match ? FlightPolicy.ExceptionState.CONNECTED : FlightPolicy.ExceptionState.CLEAR,
                    (match ? "Exception: " : "Connected to ") + ssid, ssid);
        } catch (RuntimeException e) {
            return new Wifi(exemptions.isEmpty() ? FlightPolicy.ExceptionState.CLEAR : FlightPolicy.ExceptionState.UNKNOWN,
                    "Wi-Fi state unavailable", "");
        }
    }
}
