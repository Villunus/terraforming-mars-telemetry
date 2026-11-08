package dev.tmmissioncontrol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Small always-on-top window shown next to the GitHub form, styled like the dashboard: Copy puts the diagnostics
 * zip on the clipboard as a file, which the form uploads when pasted into the "What went wrong?" box.
 */
final class BugReportWindow {
    // The dashboard's palette (style.css :root).
    private static final Color BG = new Color(0x140c0a);
    private static final Color PANEL = new Color(0x231614);
    private static final Color INK = new Color(0xf4e6d4);
    private static final Color MUTED = new Color(0xb89f88);
    private static final Color ACCENT = new Color(0xe07a3d);
    private static final Color GREEN = new Color(0x7dae6a);
    private static final Color LINE = new Color(0x3c2a24);
    /** Your board's blue (.color-blue), framed the same way: a thick band on top, a thin line around. */
    private static final Color BLUE = new Color(0x3d7ec9);
    private static final String FONT = "Segoe UI";

    /** Where Report a bug was clicked: the screen point, plus where it sat in the page when it came from the dashboard. */
    record Anchor(Point pointer, GraphicsConfiguration screen, PageClick page) {
    }

    /** A dashboard click, in CSS pixels: its position in the page, the page's width, and devicePixelRatio. */
    record PageClick(double x, double y, double width, double dpr) {
    }

    private static JFrame frame;
    private static Path zip;
    private static JButton copy;
    private static JLabel status;

    private BugReportWindow() {
    }

    /**
     * Read the pointer now, at the click, not after the zip has taken a second or two to write. A window still
     * open from an earlier report is hidden now too, so it reappears in its new place instead of jumping there.
     */
    static Anchor anchor(PageClick page) {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.setVisible(false);
            }
        });
        PointerInfo pointer = GraphicsEnvironment.isHeadless() ? null : MouseInfo.getPointerInfo();
        if (pointer == null) {
            return null;
        }
        return new Anchor(pointer.getLocation(), pointer.getDevice().getDefaultConfiguration(), page);
    }

    static void show(Path file, Anchor anchor) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            zip = file;
            if (frame == null) {
                build();
            }
            copy.setText("Copy");
            status.setText(" ");
            frame.pack();
            place(anchor);
            frame.setVisible(true);
            frame.toFront();
            copy.requestFocusInWindow();
        });
    }

    /**
     * From the dashboard, over the browser window's right edge, level with the top of the GitHub form: the clicked
     * button's place in the page and the pointer give the browser's position on screen. A browser that fills the
     * monitor leaves no room, so then it sits against the monitor's right edge. From the tray, just above the
     * tray. Always kept on the monitor that was clicked and clear of the taskbar.
     */
    private static void place(Anchor anchor) {
        if (anchor == null) {
            frame.setLocationRelativeTo(null);
            return;
        }
        Rectangle bounds = anchor.screen().getBounds();
        Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(anchor.screen());
        Rectangle area = new Rectangle(bounds.x + taskbar.left, bounds.y + taskbar.top,
                bounds.width - taskbar.left - taskbar.right, bounds.height - taskbar.top - taskbar.bottom);
        Point p = anchor.pointer();
        int x;
        int y;
        PageClick page = anchor.page();
        if (page != null && page.width() > 0) {
            // CSS pixels to Java's screen units: devicePixelRatio is the Windows scale times the browser zoom.
            double scale = anchor.screen().getDefaultTransform().getScaleX();
            double k = page.dpr() > 0 ? page.dpr() / scale : 1;
            double pageLeft = p.x - page.x() * k;
            double pageTop = p.y - page.y() * k;
            // A third over the browser's edge, so it reads as part of the report, the rest clear of the form.
            x = (int) Math.round(pageLeft + page.width() * k - frame.getWidth() * 0.3);
            y = (int) Math.round(pageTop + 190 * k); // level with the form's first box
        } else {
            x = p.x - frame.getWidth() / 2;
            y = p.y - frame.getHeight() - 32;
        }
        x = Math.max(area.x, Math.min(x, area.x + area.width - frame.getWidth()));
        y = Math.max(area.y, Math.min(y, area.y + area.height - frame.getHeight()));
        frame.setLocation(x, y);
    }

    private static void build() {
        frame = new JFrame("Report a bug");
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        List<Image> icons = MissionControlTray.appIcons();
        frame.setIconImages(icons);
        // See-through outside the rounded shape that RoundedFrame paints.
        frame.setBackground(new Color(0, 0, 0, 0));

        JComponent header = header(icons);
        RoundedFrame root = new RoundedFrame(header);
        root.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(14, 18, 4, 18));
        body.add(label("Attach game diagnostics to your bug report", INK, Font.BOLD, 16));
        body.add(Box.createVerticalStrut(2));
        body.add(label("Diagnostics helps developers debug the issue", MUTED, Font.PLAIN, 13));
        body.add(Box.createVerticalStrut(12));
        body.add(step("1", "Click <b>Copy</b>."));
        body.add(Box.createVerticalStrut(6));
        body.add(step("2", "In the GitHub form, click the <b>What went wrong?</b> box and press <b>Ctrl+V</b>."));
        body.add(Box.createVerticalStrut(6));
        body.add(step("3", "Write a sentence about what went wrong, add a title, and click <b>Create</b>."));
        body.add(Box.createVerticalStrut(10));
        status = label(" ", GREEN, Font.PLAIN, 13);
        body.add(status);
        root.add(body, BorderLayout.CENTER);

        copy = new PillButton("Copy", true);
        copy.addActionListener(e -> copyZip());
        JButton folder = new PillButton("Show in folder", false);
        folder.addActionListener(e -> showInFolder());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 14));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 10, 2, 10));
        buttons.add(folder);
        buttons.add(copy);
        root.add(buttons, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.getRootPane().setDefaultButton(copy);
    }

    /** Logo, the app's small uppercase label, and a close ×. Dragging it moves the window. */
    private static JComponent header(List<Image> icons) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false); // RoundedFrame paints its background, rounded to fit the top corners
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(6, 12, 6, 6)));
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        Image logo = trayArt();
        if (logo != null) {
            title.add(new Logo(logo));
            title.add(Box.createHorizontalStrut(10));
        }
        JLabel text = label("REPORT A BUG", MUTED, Font.BOLD, 11);
        text.setPreferredSize(new Dimension(text.getPreferredSize().width, 30));
        title.add(text);
        header.add(title, BorderLayout.WEST);
        header.add(new CloseButton(), BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            private Point start;

            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point at = frame.getLocation();
                frame.setLocation(at.x + e.getX() - start.x, at.y + e.getY() - start.y);
            }
        };
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);
        return header;
    }

    private static JComponent step(String number, String html) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel n = label(number, ACCENT, Font.BOLD, 14);
        n.setVerticalAlignment(JLabel.TOP);
        row.add(n, BorderLayout.WEST);
        row.add(label("<html><span style='color:#b89f88'>" + html.replace("<b>", "<b style='color:#f4e6d4'>")
                + "</span></html>", MUTED, Font.PLAIN, 14), BorderLayout.CENTER);
        return row;
    }

    private static JLabel label(String text, Color color, int style, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(FONT, style, size));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * The window's rounded shape, like the dashboard's boards (12 px corners): blue outside, with a thicker band
     * on top, then the dark header strip and the panel inside, each rounded to fit within the blue.
     */
    private static final class RoundedFrame extends JPanel {
        private static final int RADIUS = 12;
        private static final int TOP = 6;
        private static final int SIDE = 3;
        private final JComponent header;

        RoundedFrame(JComponent header) {
            super(new BorderLayout());
            this.header = header;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(TOP, SIDE, SIDE, SIDE));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(BLUE);
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, RADIUS * 2, RADIUS * 2));
            Shape inside = new RoundRectangle2D.Double(SIDE, TOP, w - SIDE * 2, h - TOP - SIDE,
                    (RADIUS - SIDE) * 2, (RADIUS - SIDE) * 2);
            g2.setColor(PANEL);
            g2.fill(inside);
            g2.clip(inside);
            g2.setColor(BG);
            g2.fillRect(0, 0, w, TOP + header.getHeight());
            g2.dispose();
        }
    }

    /** The largest tray art: the planet on its own, without the app icon's dark tile. */
    private static Image trayArt() {
        try (java.io.InputStream in = BugReportWindow.class.getResourceAsStream("/tray/tray-48.png")) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * The app's planet at the close button's size. Drawn from the 48 px art each paint, so a scaled display gets
     * a sharp image rather than a small bitmap stretched up.
     */
    private static final class Logo extends JComponent {
        private final Image image;

        Logo(Image image) {
            this.image = image;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(30, 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(image, 1, 1, 28, 28, null);
            g2.dispose();
        }
    }

    /** A drawn ×, in a round outlined button that turns orange on hover, so it's obvious how to close the window. */
    private static final class CloseButton extends JButton {
        CloseButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setToolTipText("Close");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> frame.setVisible(false));
            getModel().addChangeListener(e -> repaint());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(30, 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight()) - 2;
            int x0 = (getWidth() - size) / 2;
            int y0 = (getHeight() - size) / 2;
            boolean hot = getModel().isRollover() || getModel().isPressed();
            g2.setColor(hot ? ACCENT : PANEL);
            g2.fillOval(x0, y0, size, size);
            g2.setColor(hot ? ACCENT : MUTED);
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(x0, y0, size, size);
            g2.setColor(hot ? BG : INK);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int inset = size * 5 / 16;
            g2.drawLine(x0 + inset, y0 + inset, x0 + size - inset, y0 + size - inset);
            g2.drawLine(x0 + size - inset, y0 + inset, x0 + inset, y0 + size - inset);
            g2.dispose();
        }
    }

    /** The dashboard's rounded pill buttons: outlined like Past games, or filled orange for the main action. */
    private static final class PillButton extends JButton {
        private final boolean primary;

        PillButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(new Font(FONT, primary ? Font.BOLD : Font.PLAIN, 13));
            setForeground(primary ? BG : MUTED);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(5, 16, 5, 16));
            getModel().addChangeListener(e -> {
                if (!primary) {
                    setForeground(getModel().isRollover() ? INK : MUTED);
                }
                repaint();
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 84), d.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            boolean hot = getModel().isRollover() || getModel().isPressed();
            if (primary) {
                g2.setColor(hot ? ACCENT.brighter() : ACCENT);
                g2.fillRoundRect(0, 0, w, h, h, h);
            } else {
                g2.setColor(hot ? ACCENT : LINE);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w, h, h, h);
            }
            if (isFocusOwner() && !primary) {
                g2.setColor(ACCENT);
                g2.drawRoundRect(0, 0, w, h, h, h);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static void copyZip() {
        File file = zip.toFile();
        Transferable files = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return List.of(file);
            }
        };
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(files, null);
            copy.setText("Copied");
            status.setForeground(GREEN);
            status.setText("Copied. Now paste it into the form with Ctrl+V.");
        } catch (Exception ex) {
            status.setForeground(MUTED);
            status.setText("Couldn't copy. Use Show in folder and drag the zip into the form.");
        }
    }

    private static void showInFolder() {
        try {
            new ProcessBuilder("explorer.exe", "/select," + zip).start();
        } catch (Exception ignored) {
        }
    }
}
