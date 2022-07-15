package com.qa.util;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class DriverUtil {
    private static final String DRIVER_LINUX_x86 = "chromedriver-linux-amd64";
    private static final String DRIVER_MAC_x86 = "chromedriver-mac-amd64";
    private static final String DRIVER_MAC_ARM = "chromedriver-mac-aarch64";
    private static final String DRIVER_WINDOWS = "chromedriver-windows.exe";

    public static String getDriverPath() throws URISyntaxException {
        String resStr = "";
        if (OSValidator.IS_WINDOWS){
            resStr = DRIVER_WINDOWS;
        } else if (OSValidator.IS_UNIX || OSValidator.IS_SOLARIS) {
            resStr = DRIVER_LINUX_x86;
        } else if (OSValidator.IS_MAC) {
            if (System.getProperty("os.arch").equals("aarch64")) {
                resStr = DRIVER_MAC_ARM;
            } else {
                resStr = DRIVER_MAC_x86;
            }
        }
        URL res = DriverUtil.class.getClassLoader().getResource("drivers" + File.separator + resStr);
        File file = Paths.get(res.toURI()).toFile();
        return file.getAbsolutePath();
    }
}
