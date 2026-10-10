package dev.pocketflight;
interface IFlightBridge {
    String setAirplaneMode(boolean enabled) = 1;
    int connectedBluetooth(in String[] addresses) = 2;
    String probe() = 3;
    void destroy() = 16777114;
}
