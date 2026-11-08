package dev.tmmissioncontrol;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Tray icon so the windowless app can be opened or quit. */
final class MissionControlTray {
    private static TrayIcon icon;

    private MissionControlTray() {
    }

    static void install(URI openUri) {
        if (!SystemTray.isSupported()) {
            return;
        }
        Image image = loadIcon();
        if (image == null) {
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open");
        open.addActionListener(e -> browse(openUri));
        MenuItem bug = new MenuItem("Report a bug");
        bug.addActionListener(e -> reportBug());
        MenuItem quit = new MenuItem("Quit");
        quit.addActionListener(e -> quit());
        menu.add(open);
        menu.add(bug);
        menu.add(quit);

        TrayIcon trayIcon = new TrayIcon(image, "Terraforming Mars Telemetry", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> browse(openUri));
        try {
            SystemTray.getSystemTray().add(trayIcon);
            icon = trayIcon;
        } catch (AWTException ignored) {
        }
    }

    static void remove() {
        if (icon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
        }
    }

    private static void browse(URI uri) {
        if (uri == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (Exception ignored) {
        }
    }

    /** Save the diagnostics zip, open the GitHub bug form, and show the window with the zip's Copy button. */
    private static void reportBug() {
        BugReportWindow.Anchor anchor = BugReportWindow.anchor(null);
        // Off the AWT thread: slicing a large Player.log takes a few seconds.
        Thread worker = new Thread(() -> {
            try {
                BugReportWindow.show(BugReport.saveToDownloads(), anchor);
            } catch (Exception ignored) {
            }
            browse(URI.create(BugReport.issueUrl()));
        }, "bug-report");
        worker.setDaemon(true);
        worker.start();
    }

    private static void quit() {
        remove();
        App.exit(0);
    }

    /** Tray art drawn for each size Windows uses (scripts/generate-small-icons.py); 16 is the 100% scale size. */
    private static final int[] TRAY_SIZES = {16, 20, 24, 32, 40, 48};

    /**
     * The tray draws the icon at 16 px times the display scale. A multi-resolution image lets it pick the
     * variant made for that size instead of shrinking the 1024 px app icon into a smudge.
     */
    private static Image loadIcon() {
        List<Image> variants = new ArrayList<>();
        for (int size : TRAY_SIZES) {
            try (InputStream in = MissionControlTray.class.getResourceAsStream("/tray/tray-" + size + ".png")) {
                BufferedImage img = in == null ? null : ImageIO.read(in);
                if (img != null) {
                    variants.add(img);
                }
            } catch (Exception ignored) {
            }
        }
        if (!variants.isEmpty()) {
            return new BaseMultiResolutionImage(variants.toArray(new Image[0]));
        }
        return loadAppIcon();
    }

    /** Icons for an app window's title bar and taskbar button: the small tray art plus the full app icon. */
    static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : TRAY_SIZES) {
            try (InputStream in = MissionControlTray.class.getResourceAsStream("/tray/tray-" + size + ".png")) {
                BufferedImage img = in == null ? null : ImageIO.read(in);
                if (img != null) {
                    icons.add(img);
                }
            } catch (Exception ignored) {
            }
        }
        Image large = loadAppIcon();
        if (large != null) {
            icons.add(large);
        }
        return icons;
    }

    private static Image loadAppIcon() {
        try (InputStream in = MissionControlTray.class.getResourceAsStream("/icon.png")) {
            if (in == null) {
                return null;
            }
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                return null;
            }
            int size = 64;
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, size, size, null);
            g.dispose();
            return scaled;
        } catch (Exception ignored) {
            return null;
        }
    }
}
