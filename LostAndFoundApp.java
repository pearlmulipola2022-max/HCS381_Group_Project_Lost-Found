import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HCS 381 Java Swing Project
 * Campus Lost & Found
 *
 * A desktop application that lets campus users report lost/found items,
 * search the item register, and record when an item has been returned.
 *
 * Data is saved locally in campus_lost_found.csv in the application's
 * working directory. This is a learning-project prototype, not a secure
 * production database.
 *
 * Theme: dark navy with a warm orange accent.
 */
public class LostAndFoundApp extends JFrame {
    private static final String DATA_FILE = "campus_lost_found.csv";

    // ---- Colour palette (from the reference design) ----
    private static final Color BG        = new Color(0x171E33); // page background
    private static final Color PANEL     = new Color(0x2D3250); // sidebar, cards
    private static final Color PANEL_HI  = new Color(0x424769); // inputs, headers, hover
    private static final Color MUTED     = new Color(0x676F9D); // borders, scrollbars
    private static final Color ACCENT    = new Color(0xF9B17A); // orange accent
    private static final Color ACCENT_HI = new Color(0xFFC59A); // accent hover
    private static final Color ACCENT_LO = new Color(0xE79A5F); // accent pressed
    private static final Color TEXT      = Color.WHITE;
    private static final Color TEXT_SOFT = new Color(0xB4BAE0); // secondary text
    private static final Color ROW_ALT   = new Color(0x272C4A); // table zebra row
    private static final Color GREEN     = new Color(0x6FD6AE); // "Found"
    private static final Color LILAC     = new Color(0xC39BE8); // "Returned"
    private static final Color RED       = new Color(0xFF8A8A); // delete

    private enum Kind { PRIMARY, SECONDARY, DANGER, NAV }

    private final List<LostItem> items = new ArrayList<>();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, RoundButton> navButtons = new HashMap<>();

    private DefaultTableModel tableModel;
    private JTable itemTable;
    private JTextField searchField;
    private JComboBox<String> statusFilter;
    private JLabel totalLabel, lostLabel, foundLabel, returnedLabel;

    private static String fontFamily;

    /** Uses Raleway if installed (as in the reference design), then Segoe UI, then the default sans-serif. */
    private static Font font(int style, int size) {
        if (fontFamily == null) {
            fontFamily = "SansSerif";
            String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            boolean raleway = false, segoe = false;
            for (String n : names) {
                if (n.equals("Raleway")) raleway = true;
                if (n.equals("Segoe UI")) segoe = true;
            }
            if (raleway) fontFamily = "Raleway";
            else if (segoe) fontFamily = "Segoe UI";
        }
        return new Font(fontFamily, style, size);
    }

    public LostAndFoundApp() {
        setTitle("Campus Lost & Found");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        setSize(1120, 720);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        loadItems();
        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);

        contentPanel.setBackground(BG);
        contentPanel.add(buildDashboard(), "dashboard");
        contentPanel.add(buildItemsPage(), "items");
        contentPanel.add(buildReportPage(), "report");
        add(contentPanel, BorderLayout.CENTER);

        showPage("dashboard");
    }

    // =====================================================================
    // Custom-painted components (these ignore the OS look-and-feel, which is
    // why the colours now look the same on every computer)
    // =====================================================================

    /** A flat rounded button that paints its own colours. */
    private static class RoundButton extends JButton {
        private final Kind kind;
        private boolean hover, active;

        RoundButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(font(Font.BOLD, kind == Kind.NAV ? 14 : 13));
            if (kind == Kind.NAV) {
                setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(new EmptyBorder(0, 22, 0, 8));
            } else {
                setBorder(new EmptyBorder(11, 22, 11, 22));
            }
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setActive(boolean value) { active = value; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean pressed = getModel().isPressed();
            Color fill = null, border = null, fg = TEXT;
            switch (kind) {
                case PRIMARY:
                    fill = pressed ? ACCENT_LO : hover ? ACCENT_HI : ACCENT;
                    fg = PANEL;
                    break;
                case SECONDARY:
                    fill = pressed ? PANEL_HI : hover ? new Color(0x383D5E) : null;
                    border = MUTED;
                    break;
                case DANGER:
                    fill = hover ? new Color(0x4A2F48) : null;
                    border = new Color(0xB86A7A);
                    fg = RED;
                    break;
                case NAV:
                    fill = active ? PANEL_HI : hover ? new Color(0x383D5E) : null;
                    fg = active ? TEXT : TEXT_SOFT;
                    break;
            }
            if (fill != null) { g.setColor(fill); g.fillRoundRect(0, 0, w, h, 14, 14); }
            if (border != null) { g.setColor(border); g.drawRoundRect(0, 0, w - 1, h - 1, 14, 14); }
            if (kind == Kind.NAV && active) {
                g.setColor(ACCENT);
                g.fillRoundRect(0, h / 2 - 12, 4, 24, 4, 4);
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            Insets in = getInsets();
            int tx = getHorizontalAlignment() == SwingConstants.LEFT
                    ? in.left : (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(fg);
            g.drawString(getText(), tx, ty);
            g.dispose();
        }
    }

    /** A panel with rounded corners and an optional coloured bar on its left edge. */
    private static class RoundPanel extends JPanel {
        private final Color fill;
        private final int arc;
        private Color accent;

        RoundPanel(LayoutManager layout, Color fill, int arc) {
            super(layout);
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        void setAccent(Color accent) { this.accent = accent; }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            if (accent != null) {
                g.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
                g.setColor(accent);
                g.fillRect(0, 0, 5, getHeight());
            }
            g.dispose();
        }
    }

    /** The welcome banner: navy gradient with a small mountain-sunset scene on the right. */
    private static class HeroPanel extends JPanel {
        HeroPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.clip(new RoundRectangle2D.Float(0, 0, w, h, 24, 24));

            g.setPaint(new GradientPaint(0, 0, PANEL_HI, w, 0, PANEL));
            g.fillRect(0, 0, w, h);

            int sx = (int) (w * 0.62), sw = w - sx;
            g.setPaint(new GradientPaint(0, 0, MUTED, 0, h, ACCENT));
            g.fillRect(sx, 0, sw, h);

            g.setColor(PANEL_HI);
            g.fillPolygon(
                    new int[]{sx, sx + (int) (.20 * sw), sx + (int) (.34 * sw), sx + (int) (.58 * sw),
                            sx + (int) (.80 * sw), w, w, sx},
                    new int[]{(int) (.72 * h), (int) (.34 * h), (int) (.55 * h), (int) (.22 * h),
                            (int) (.50 * h), (int) (.40 * h), h, h}, 8);

            g.setPaint(new GradientPaint(0, (int) (.66 * h), new Color(103, 111, 157, 0),
                    0, h, new Color(66, 71, 105, 255)));
            g.fillRect(sx, (int) (.66 * h), sw, h);

            g.setColor(PANEL);
            g.fillPolygon(
                    new int[]{sx, sx + (int) (.26 * sw), sx + (int) (.44 * sw), sx + (int) (.70 * sw), w, w, sx},
                    new int[]{(int) (.92 * h), (int) (.74 * h), (int) (.86 * h), (int) (.72 * h),
                            (int) (.88 * h), h, h}, 7);
            g.dispose();
        }
    }

    /** Thin dark scroll bar with an orange hover. */
    private static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = MUTED;
            trackColor = PANEL;
        }
        @Override protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
        private JButton emptyButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero); b.setMinimumSize(zero); b.setMaximumSize(zero);
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(PANEL);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? ACCENT : MUTED);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    // =====================================================================
    // Sidebar and page shell
    // =====================================================================

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(PANEL);
        sidebar.setPreferredSize(new Dimension(235, 0));
        sidebar.setBorder(new EmptyBorder(30, 16, 20, 16));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("CAMPUS");
        logo.setForeground(TEXT);
        logo.setFont(font(Font.BOLD, 26));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        logo.setBorder(new EmptyBorder(0, 6, 0, 0));
        JLabel subtitle = new JLabel("LOST & FOUND");
        subtitle.setForeground(ACCENT);
        subtitle.setFont(font(Font.BOLD, 13));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(0, 6, 0, 0));

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(2));
        sidebar.add(subtitle);
        sidebar.add(Box.createVerticalStrut(40));
        sidebar.add(navButton("Dashboard", "dashboard"));
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(navButton("Browse items", "items"));
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(navButton("Report an item", "report"));
        sidebar.add(Box.createVerticalGlue());

        RoundPanel helpCard = new RoundPanel(new BorderLayout(), PANEL_HI, 16);
        helpCard.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel help = new JLabel("<html><div style='width:135px;color:#E3E6F7'><b>Need help?</b><br>Contact campus reception or student services to verify ownership.</div></html>");
        help.setFont(font(Font.PLAIN, 12));
        helpCard.add(help, BorderLayout.CENTER);
        helpCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        helpCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, helpCard.getPreferredSize().height));
        sidebar.add(helpCard);
        sidebar.add(Box.createVerticalStrut(12));

        JLabel version = new JLabel("HCS 381 \u2022 Java Swing");
        version.setForeground(MUTED);
        version.setFont(font(Font.PLAIN, 11));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setBorder(new EmptyBorder(0, 6, 0, 0));
        sidebar.add(version);
        return sidebar;
    }

    private RoundButton navButton(String text, String page) {
        RoundButton button = new RoundButton(text, Kind.NAV);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setPreferredSize(new Dimension(190, 46));
        button.addActionListener(e -> showPage(page));
        navButtons.put(page, button);
        return button;
    }

    private JPanel pageShell(String title, String description) {
        JPanel page = new JPanel(new BorderLayout(0, 22));
        page.setBackground(BG);
        page.setBorder(new EmptyBorder(30, 32, 28, 32));

        JPanel heading = new JPanel();
        heading.setBackground(BG);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(font(Font.BOLD, 28));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel bar = new JPanel();
        bar.setBackground(ACCENT);
        bar.setPreferredSize(new Dimension(46, 4));
        bar.setMaximumSize(new Dimension(46, 4));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel descLabel = new JLabel(description);
        descLabel.setForeground(TEXT_SOFT);
        descLabel.setFont(font(Font.PLAIN, 14));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(8));
        heading.add(bar);
        heading.add(Box.createVerticalStrut(10));
        heading.add(descLabel);
        page.add(heading, BorderLayout.NORTH);
        return page;
    }

    // =====================================================================
    // Dashboard
    // =====================================================================

    private JPanel buildDashboard() {
        JPanel page = pageShell("Good day, campus community", "A simple place to report, search, and reunite people with their belongings.");
        JPanel body = new JPanel();
        body.setBackground(BG);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        HeroPanel hero = new HeroPanel();
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(new EmptyBorder(26, 30, 26, 12));
        JLabel head = new JLabel("<html><div style='width:360px'><span style='color:#FFFFFF'>Lost something?</span><br><span style='color:#F9B17A'>Found something?</span></div></html>");
        head.setFont(font(Font.BOLD, 26));
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = new JLabel("<html><div style='width:360px;color:#D5D9F2'>Report it here so the right person can find their item.</div></html>");
        sub.setFont(font(Font.PLAIN, 14));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        RoundButton reportNow = new RoundButton("+  Report an item", Kind.PRIMARY);
        reportNow.setAlignmentX(Component.LEFT_ALIGNMENT);
        reportNow.addActionListener(e -> showPage("report"));
        left.add(head);
        left.add(Box.createVerticalStrut(10));
        left.add(sub);
        left.add(Box.createVerticalStrut(18));
        left.add(reportNow);
        hero.add(left, BorderLayout.WEST);
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        hero.setPreferredSize(new Dimension(0, 220));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        body.add(hero);
        body.add(Box.createVerticalStrut(22));

        JPanel stats = new JPanel(new GridLayout(1, 4, 14, 0));
        stats.setBackground(BG);
        totalLabel = new JLabel("0");
        lostLabel = new JLabel("0");
        foundLabel = new JLabel("0");
        returnedLabel = new JLabel("0");
        stats.add(statCard("Total reports", totalLabel, new Color(0x8F98D8)));
        stats.add(statCard("Lost items", lostLabel, ACCENT));
        stats.add(statCard("Found items", foundLabel, GREEN));
        stats.add(statCard("Returned", returnedLabel, LILAC));
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        body.add(stats);
        body.add(Box.createVerticalStrut(22));

        JPanel how = new JPanel(new GridLayout(1, 3, 14, 0));
        how.setBackground(BG);
        how.add(infoCard("1", "Report", "Add the item name, location, date, and your contact details."));
        how.add(infoCard("2", "Search", "Browse the list and filter reports to find a possible match."));
        how.add(infoCard("3", "Reconnect", "Contact the reporter and verify ownership before returning an item."));
        how.setAlignmentX(Component.LEFT_ALIGNMENT);
        how.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        body.add(how);
        body.add(Box.createVerticalStrut(18));

        JLabel note = new JLabel("<html><font color='#F9B17A'><b>Safety tip:</b></font> Don't post passwords, PINs, or full private identification details. Verify ownership before handing over valuable items.</html>");
        note.setForeground(TEXT_SOFT);
        note.setFont(font(Font.PLAIN, 12));
        note.setBorder(new EmptyBorder(4, 2, 0, 2));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(note);
        body.add(Box.createVerticalGlue());

        page.add(body, BorderLayout.CENTER);
        return page;
    }

    private JPanel statCard(String title, JLabel number, Color accent) {
        RoundPanel card = new RoundPanel(new BorderLayout(0, 6), PANEL, 18);
        card.setAccent(accent);
        card.setBorder(new EmptyBorder(16, 24, 16, 14));
        JLabel label = new JLabel(title);
        label.setForeground(TEXT_SOFT);
        label.setFont(font(Font.PLAIN, 13));
        number.setForeground(TEXT);
        number.setFont(font(Font.BOLD, 34));
        card.add(label, BorderLayout.NORTH);
        card.add(number, BorderLayout.CENTER);
        return card;
    }

    private JPanel infoCard(String number, String title, String description) {
        RoundPanel card = new RoundPanel(null, PANEL, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        RoundPanel badge = new RoundPanel(new GridBagLayout(), ACCENT, 34);
        JLabel n = new JLabel(number);
        n.setForeground(PANEL);
        n.setFont(font(Font.BOLD, 15));
        badge.add(n);
        Dimension size = new Dimension(34, 34);
        badge.setPreferredSize(size);
        badge.setMinimumSize(size);
        badge.setMaximumSize(size);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(badge);
        card.add(Box.createVerticalStrut(12));

        JLabel t = new JLabel(title);
        t.setForeground(TEXT);
        t.setFont(font(Font.BOLD, 16));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(t);
        card.add(Box.createVerticalStrut(6));
        JLabel d = new JLabel("<html><div style='width:175px;color:#B4BAE0'>" + description + "</div></html>");
        d.setFont(font(Font.PLAIN, 12));
        d.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(d);
        return card;
    }

    // =====================================================================
    // Browse items
    // =====================================================================

    private JPanel buildItemsPage() {
        JPanel page = pageShell("Browse item reports", "Search by item, location, category, or status.");
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(BG);

        JPanel tools = new JPanel(new BorderLayout(12, 0));
        tools.setBackground(BG);
        searchField = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(TEXT_SOFT);
                    g2.setFont(getFont());
                    Insets in = getInsets();
                    g2.drawString("Search by item, location, category, or name...", in.left,
                            (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent());
                    g2.dispose();
                }
            }
        };
        styleField(searchField);
        searchField.setToolTipText("Search item name, category, location, or description");
        searchField.addActionListener(e -> refreshTable());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshTable(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshTable(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshTable(); }
        });
        tools.add(searchField, BorderLayout.CENTER);

        statusFilter = new JComboBox<>(new String[]{"All statuses", "Lost", "Found", "Returned"});
        styleCombo(statusFilter);
        statusFilter.setPreferredSize(new Dimension(170, 42));
        statusFilter.addActionListener(e -> refreshTable());
        tools.add(statusFilter, BorderLayout.EAST);
        body.add(tools, BorderLayout.NORTH);

        String[] columns = {"ID", "Type", "Item", "Category", "Location", "Date", "Status", "Reporter / Contact"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        itemTable = new JTable(tableModel);
        itemTable.setRowHeight(38);
        itemTable.setFont(font(Font.PLAIN, 13));
        itemTable.setBackground(PANEL);
        itemTable.setForeground(TEXT);
        itemTable.setShowGrid(false);
        itemTable.setIntercellSpacing(new Dimension(0, 0));
        itemTable.setFillsViewportHeight(true);
        itemTable.setSelectionBackground(ACCENT);
        itemTable.setSelectionForeground(PANEL);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        itemTable.setDefaultRenderer(Object.class, new ItemCellRenderer());

        JTableHeader header = itemTable.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                     boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, false, false, row, col);
                setOpaque(true);
                setBackground(PANEL_HI);
                setForeground(TEXT);
                setFont(font(Font.BOLD, 12));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT),
                        new EmptyBorder(0, 12, 0, 8)));
                return this;
            }
        });

        int[] widths = {45, 60, 150, 140, 100, 95, 80, 170};
        for (int i = 0; i < widths.length; i++) itemTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JScrollPane scroll = new JScrollPane(itemTable);
        scroll.setBorder(BorderFactory.createLineBorder(PANEL_HI));
        scroll.getViewport().setBackground(PANEL);
        styleScrollBars(scroll);
        JPanel corner = new JPanel();
        corner.setBackground(PANEL);
        scroll.setCorner(JScrollPane.LOWER_RIGHT_CORNER, corner);
        body.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setBackground(BG);
        RoundButton refresh = new RoundButton("Refresh", Kind.SECONDARY);
        refresh.addActionListener(e -> refreshTable());
        RoundButton claim = new RoundButton("Mark as returned", Kind.PRIMARY);
        claim.addActionListener(e -> markReturned());
        RoundButton delete = new RoundButton("Delete report", Kind.DANGER);
        delete.addActionListener(e -> deleteSelected());
        actions.add(refresh);
        actions.add(claim);
        actions.add(delete);
        body.add(actions, BorderLayout.SOUTH);

        page.add(body, BorderLayout.CENTER);
        return page;
    }

    /** Dark rows, orange selection, and a coloured Status column. */
    private static class ItemCellRenderer extends DefaultTableCellRenderer {
        private final EmptyBorder pad = new EmptyBorder(0, 12, 0, 8);

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                 boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            setBorder(pad);
            setFont(font(Font.PLAIN, 13));
            if (sel) {
                setBackground(ACCENT);
                setForeground(PANEL);
            } else {
                setBackground(row % 2 == 0 ? PANEL : ROW_ALT);
                setForeground(TEXT);
                if (col == 6) {
                    String s = String.valueOf(v);
                    setFont(font(Font.BOLD, 13));
                    setForeground("Lost".equals(s) ? ACCENT : "Found".equals(s) ? GREEN : LILAC);
                }
            }
            return this;
        }
    }

    // =====================================================================
    // Report an item
    // =====================================================================

    private JPanel buildReportPage() {
        JPanel page = pageShell("Report a lost or found item", "Complete the details below. Fields marked * are required.");
        RoundPanel form = new RoundPanel(new GridBagLayout(), PANEL, 20);
        form.setBorder(new EmptyBorder(24, 28, 22, 28));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        JComboBox<String> type = new JComboBox<>(new String[]{"Lost", "Found"});
        JTextField name = new JTextField();
        JComboBox<String> category = new JComboBox<>(new String[]{"Phone / Electronics", "Wallet / ID", "Keys", "Books / Stationery", "Clothing / Bag", "Jewellery", "Other"});
        JTextField location = new JTextField();
        JTextField date = new JTextField(LocalDate.now().toString());
        JTextField reporter = new JTextField();
        JTextField contact = new JTextField();
        JTextArea description = new JTextArea(4, 20);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        styleField(description);
        JScrollPane descriptionScroll = new JScrollPane(description);
        descriptionScroll.setBorder(null);
        descriptionScroll.getViewport().setBackground(PANEL_HI);
        styleScrollBars(descriptionScroll);

        addFormRow(form, c, 0, "Report type *", type, "Item name *", name);
        addFormRow(form, c, 1, "Category *", category, "Campus location *", location);
        addFormRow(form, c, 2, "Date (YYYY-MM-DD) *", date, "Your name *", reporter);
        addFormRow(form, c, 3, "Contact details *", contact, "Description / identifying details", descriptionScroll);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        RoundButton clear = new RoundButton("Clear form", Kind.SECONDARY);
        clear.addActionListener(e -> {
            name.setText(""); location.setText(""); date.setText(LocalDate.now().toString());
            reporter.setText(""); contact.setText(""); description.setText(""); type.setSelectedIndex(0); category.setSelectedIndex(0);
        });
        RoundButton submit = new RoundButton("Submit report", Kind.PRIMARY);
        submit.addActionListener(e -> {
            String itemName = name.getText().trim();
            String loc = location.getText().trim();
            String dateValue = date.getText().trim();
            String person = reporter.getText().trim();
            String contactValue = contact.getText().trim();
            if (itemName.isEmpty() || loc.isEmpty() || dateValue.isEmpty() || person.isEmpty() || contactValue.isEmpty()) {
                showError("Please complete all required fields.");
                return;
            }
            try {
                LocalDate.parse(dateValue);
            } catch (Exception ex) {
                showError("Enter a valid date in YYYY-MM-DD format.");
                return;
            }
            LostItem item = new LostItem(nextId(), (String) type.getSelectedItem(), itemName,
                    (String) category.getSelectedItem(), loc, dateValue, person, contactValue,
                    description.getText().trim(), "Lost".equals(type.getSelectedItem()) ? "Lost" : "Found");
            items.add(item);
            saveItems();
            updateStats();
            refreshTable();
            JOptionPane.showMessageDialog(this, "Report submitted successfully. Report ID: " + item.id
                            + "\n\nSaved to:\n" + new File(DATA_FILE).getAbsolutePath(),
                    "Report saved", JOptionPane.INFORMATION_MESSAGE);
            clear.doClick();
            showPage("items");
        });
        buttons.add(clear);
        buttons.add(submit);

        c.insets = new Insets(10, 10, 0, 10);
        c.gridx = 0; c.gridy = 8; c.gridwidth = 2; c.weighty = 1; c.anchor = GridBagConstraints.SOUTHEAST;
        form.add(buttons, c);
        page.add(form, BorderLayout.CENTER);
        return page;
    }

    /** One row of the form: two columns, each with its label above the field. */
    private void addFormRow(JPanel form, GridBagConstraints c, int row, String label1, JComponent field1,
                            String label2, JComponent field2) {
        c.gridwidth = 1; c.weightx = 0.5; c.weighty = 0; c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.insets = new Insets(8, 10, 3, 10);
        c.gridy = row * 2;
        c.gridx = 0; form.add(formLabel(label1), c);
        c.gridx = 1; form.add(formLabel(label2), c);

        c.insets = new Insets(0, 10, 6, 10);
        c.gridy = row * 2 + 1;
        prepareField(field1);
        prepareField(field2);
        c.gridx = 0; form.add(field1, c);
        c.gridx = 1; form.add(field2, c);
    }

    private JLabel formLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_SOFT);
        label.setFont(font(Font.BOLD, 12));
        return label;
    }

    private void prepareField(JComponent field) {
        if (field instanceof JTextField) {
            styleField((JTextField) field);
        } else if (field instanceof JComboBox) {
            styleCombo((JComboBox<?>) field);
        }
    }

    // =====================================================================
    // Styling helpers
    // =====================================================================

    private void styleField(JTextComponent f) {
        f.setOpaque(true);
        f.setBackground(PANEL_HI);
        f.setForeground(TEXT);
        f.setCaretColor(ACCENT);
        f.setSelectionColor(ACCENT);
        f.setSelectedTextColor(PANEL);
        f.setFont(font(Font.PLAIN, 13));
        setFieldBorder(f, false);
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { setFieldBorder(f, true); }
            @Override public void focusLost(FocusEvent e) { setFieldBorder(f, false); }
        });
    }

    /** Orange bar on the left of the field while it is focused (like the login field in the reference). */
    private void setFieldBorder(JComponent f, boolean focused) {
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, focused ? ACCENT : MUTED),
                new EmptyBorder(10, 10, 10, 10)));
    }

    private <T> void styleCombo(JComboBox<T> box) {
        box.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                BasicArrowButton b = new BasicArrowButton(BasicArrowButton.SOUTH, PANEL_HI, PANEL_HI, ACCENT, PANEL_HI);
                b.setBorder(new EmptyBorder(0, 0, 0, 0));
                return b;
            }
        });
        box.setBackground(PANEL_HI);
        box.setForeground(TEXT);
        box.setFont(font(Font.PLAIN, 13));
        box.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, MUTED));
        box.setPreferredSize(new Dimension(box.getPreferredSize().width, 40));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, false);
                setBorder(new EmptyBorder(8, 10, 8, 10));
                setFont(font(Font.PLAIN, 13));
                boolean hot = isSelected && index >= 0;
                setBackground(hot ? ACCENT : PANEL_HI);
                setForeground(hot ? PANEL : TEXT);
                return this;
            }
        });
        Object popup = box.getAccessibleContext().getAccessibleChild(0);
        if (popup instanceof BasicComboPopup) {
            JList<?> list = ((BasicComboPopup) popup).getList();
            list.setBackground(PANEL_HI);
            list.setForeground(TEXT);
            list.setSelectionBackground(PANEL_HI);
            list.setSelectionForeground(TEXT);
            ((BasicComboPopup) popup).setBorder(BorderFactory.createLineBorder(MUTED));
        }
    }

    private void styleScrollBars(JScrollPane scroll) {
        scroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scroll.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 12));
    }

    /** Makes pop-up dialogs, tooltips and any plain buttons match the theme. Call once at start-up. */
    private static void installTheme() {
        try {
            // Metal ignores the OS theme, so our colours look the same on every computer.
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        UIManager.put("Panel.background", PANEL);
        UIManager.put("ComboBox.background", PANEL_HI);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", PANEL_HI);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.disabledBackground", PANEL_HI);
        UIManager.put("ComboBox.disabledForeground", MUTED);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", font(Font.PLAIN, 13));
        UIManager.put("OptionPane.buttonFont", font(Font.BOLD, 12));
        UIManager.put("Button.background", ACCENT);
        UIManager.put("Button.foreground", PANEL);
        UIManager.put("Button.select", ACCENT_LO);
        UIManager.put("Button.focus", PANEL);
        UIManager.put("ToolTip.background", PANEL_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(MUTED));
    }

    // =====================================================================
    // Logic (unchanged behaviour)
    // =====================================================================

    private void showPage(String page) {
        cardLayout.show(contentPanel, page);
        for (Map.Entry<String, RoundButton> e : navButtons.entrySet()) {
            e.getValue().setActive(e.getKey().equals(page));
        }
        if ("dashboard".equals(page)) updateStats();
        if ("items".equals(page)) refreshTable();
    }

    private void refreshTable() {
        if (tableModel == null) return;
        tableModel.setRowCount(0);
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        String filter = statusFilter == null ? "All statuses" : (String) statusFilter.getSelectedItem();
        for (LostItem item : items) {
            if (!"All statuses".equals(filter) && !item.status.equals(filter)) continue;
            String searchable = (item.name + " " + item.category + " " + item.location + " " +
                    item.description + " " + item.reporter).toLowerCase();
            if (!searchable.contains(query)) continue;
            tableModel.addRow(new Object[]{item.id, item.type, item.name, item.category, item.location,
                    item.date, item.status, item.reporter + " / " + item.contact});
        }
    }

    private LostItem selectedItem() {
        if (itemTable == null || itemTable.getSelectedRow() < 0) {
            showError("Please select an item from the table first.");
            return null;
        }
        int id = Integer.parseInt(tableModel.getValueAt(itemTable.getSelectedRow(), 0).toString());
        for (LostItem item : items) if (item.id == id) return item;
        return null;
    }

    private void markReturned() {
        LostItem item = selectedItem();
        if (item == null) return;
        if ("Returned".equals(item.status)) {
            JOptionPane.showMessageDialog(this, "This item is already marked as returned.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Mark \"" + item.name + "\" as returned?",
                "Confirm return", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            item.status = "Returned";
            saveItems(); refreshTable(); updateStats();
            JOptionPane.showMessageDialog(this, "Item status updated.");
        }
    }

    private void deleteSelected() {
        LostItem item = selectedItem();
        if (item == null) return;
        int answer = JOptionPane.showConfirmDialog(this, "Delete report for \"" + item.name + "\"?",
                "Confirm delete", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            items.remove(item);
            saveItems(); refreshTable(); updateStats();
        }
    }

    /** Next free report ID (stays unique even after a report has been deleted). */
    private int nextId() {
        int max = 0;
        for (LostItem item : items) max = Math.max(max, item.id);
        return max + 1;
    }

    private void updateStats() {
        if (totalLabel == null) return;
        int lost = 0, found = 0, returned = 0;
        for (LostItem item : items) {
            if ("Returned".equals(item.status)) returned++;
            else if ("Lost".equals(item.status)) lost++;
            else if ("Found".equals(item.status)) found++;
        }
        totalLabel.setText(String.valueOf(items.size()));
        lostLabel.setText(String.valueOf(lost));
        foundLabel.setText(String.valueOf(found));
        returnedLabel.setText(String.valueOf(returned));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Check details", JOptionPane.WARNING_MESSAGE);
    }

    private static final String[] CSV_HEADERS = {"ID", "Type", "Item", "Category", "Location",
            "Date", "Reporter", "Contact", "Description", "Status"};

    private void loadItems() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) text.append(buf, 0, n);
            String content = text.toString();
            if (content.startsWith("\uFEFF")) content = content.substring(1);   // Excel BOM
            for (List<String> row : parseCsv(content)) {
                if (row.size() != 10) continue;
                try {
                    items.add(new LostItem(Integer.parseInt(row.get(0).trim()), row.get(1), row.get(2), row.get(3),
                            row.get(4), row.get(5), row.get(6), row.get(7), row.get(8), row.get(9)));
                } catch (NumberFormatException ignored) { }   // skips the header row
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not load saved reports: " + ex.getMessage());
        }
    }

    private void saveItems() {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(DATA_FILE), StandardCharsets.UTF_8))) {
            writer.write('\uFEFF');   // BOM so Excel reads the file as UTF-8
            writer.write(String.join(",", CSV_HEADERS));
            writer.write("\r\n");
            for (LostItem item : items) {
                writer.write(item.id + "," + csv(item.type) + "," + csv(item.name) + "," +
                        csv(item.category) + "," + csv(item.location) + "," + csv(item.date) + "," +
                        csv(item.reporter) + "," + csv(item.contact) + "," + csv(item.description) + "," + csv(item.status));
                writer.write("\r\n");
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save reports. If campus_lost_found.csv is open in Excel, close it and try again.\n"
                            + ex.getMessage(),
                    "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Wraps a value in quotes when needed so commas, quotes and line breaks stay inside one cell. */
    private String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Minimal CSV parser (comma separated, quoted fields, escaped quotes, line breaks inside quotes). */
    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static class LostItem {
        int id;
        String type, name, category, location, date, reporter, contact, description;
        String status;
        LostItem(int id, String type, String name, String category, String location, String date,
                 String reporter, String contact, String description, String status) {
            this.id = id; this.type = type; this.name = name; this.category = category;
            this.location = location; this.date = date; this.reporter = reporter;
            this.contact = contact; this.description = description; this.status = status;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installTheme();
            new LostAndFoundApp().setVisible(true);
        });
    }
}