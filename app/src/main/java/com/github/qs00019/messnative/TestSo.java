package com.github.qs00019.messnative;



public class TestSo {
    static {
        System.loadLibrary("native-lib");
    }

    public static native String printHello(String input);

}
