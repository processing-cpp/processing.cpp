package processing.mode.cpp;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import javax.swing.text.BadLocationException;

import processing.app.*;
import processing.app.syntax.*;
import processing.app.ui.*;
import java.util.List;
import java.util.prefs.Preferences;


public class CppEditor extends Editor {

  private CppRunner currentRunner;
  private final Set<Integer> errorLines = new HashSet<>();
  private ErrorOverlay overlay;
  private CppLinter linter;

  public CppEditor(Base base, String path,
                   EditorState state, CppMode mode) throws EditorException {
    super(base, path, state, mode);
    linter = new CppLinter(mode, this::setProblemList);
    // Hook into text area changes for live linting
    javax.swing.SwingUtilities.invokeLater(() -> {
      textarea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent e)  { scheduleLint(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e)  { scheduleLint(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleLint(); }
      });
      scheduleLint(); // initial lint on open
    });
  }

  private void scheduleLint() {
    if (linter == null) return;
    // Pass sketch for tab metadata, plus live text for the current tab
    linter.scheduleCheck(sketch, sketch.getCurrentCodeIndex(), getText());
  }

  @Override
  public void dispose() {
    if (linter != null) linter.shutdown();
    super.dispose();
  }

  // ── Bug report link (placed in the footer's tab bar, just left of the
  //    version number / "Updates" indicator) ──────────────────────────────
  private static final String BUG_REPORT_URL =
    "https://github.com/processing-cpp/processing.cpp/issues";

  private static final String[] CPP_STDS = {"c++17", "c++20", "c++23", "c++2c"};
  private static final String PREF_KEY = "cppmode.std";
  private JComboBox<String> stdCombo;

  @Override
  public EditorFooter createFooter() {
    EditorFooter ef = super.createFooter();
    SwingUtilities.invokeLater(() -> {
      addBugReportLink(ef);
      addStdDropdown(ef);
    });
    return ef;
  }

  private void addStdDropdown(EditorFooter ef) {
    JLabel versionLabel = findVersionLabel(ef);
    Container tabBar = (versionLabel != null) ? versionLabel.getParent() : null;
    if (tabBar == null) return;

    Preferences prefs = Preferences.userNodeForPackage(CppEditor.class);
    String gpp = "g++";
    String bestStd = processing.mode.cpp.CppBuild.bestCppStd(gpp);
    String savedStd = prefs.get(PREF_KEY, bestStd);

    int bestIdx = 0;
    for (int i = 0; i < CPP_STDS.length; i++)
      if (CPP_STDS[i].equals(bestStd)) { bestIdx = i; break; }
    final int maxIdx = bestIdx;

    stdCombo = new JComboBox<>(CPP_STDS);
    stdCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        int checkIdx = index < 0 ? stdCombo.getSelectedIndex() : index;
        boolean supported = checkIdx <= maxIdx;
        setEnabled(supported);
        setForeground(supported ? list.getForeground() : java.awt.Color.GRAY);
        return this;
      }
    });

    String toSelect = bestStd;
    for (int i = 0; i <= bestIdx; i++)
      if (CPP_STDS[i].equals(savedStd)) { toSelect = savedStd; break; }
    stdCombo.setSelectedItem(toSelect);
    stdCombo.setFont(versionLabel.getFont());
    stdCombo.setFocusable(false);
    stdCombo.setToolTipText("C++ standard — grayed out = not supported by your g++");

    stdCombo.addActionListener(e -> {
      int sel = stdCombo.getSelectedIndex();
      if (sel > maxIdx) { stdCombo.setSelectedIndex(maxIdx); return; }
      prefs.put(PREF_KEY, (String) stdCombo.getSelectedItem());
    });

    // Insert combo + small gap directly before the version label in the Box.
    int versionIdx = -1;
    for (int i = 0; i < tabBar.getComponentCount(); i++)
      if (tabBar.getComponent(i) == versionLabel) { versionIdx = i; break; }
    if (versionIdx >= 0) {
      tabBar.add(javax.swing.Box.createHorizontalStrut(6), versionIdx);
      tabBar.add(stdCombo, versionIdx);
      tabBar.add(javax.swing.Box.createHorizontalStrut(4), versionIdx);
    } else {
      tabBar.add(stdCombo);
    }
    tabBar.revalidate();
    tabBar.repaint();
  }

  public String getSelectedCppStd() {
    if (stdCombo != null) return (String) stdCombo.getSelectedItem();
    Preferences prefs = Preferences.userNodeForPackage(CppEditor.class);
    return prefs.get(PREF_KEY, processing.mode.cpp.CppBuild.bestCppStd("g++"));
  }

  private void addBugReportLink(EditorFooter ef) {
    JLabel prefix = new JLabel("Found a bug? Report it: ");
    JLabel link = new JLabel("<html><u>here</u></html>");
    link.setForeground(new Color(0x88, 0x66, 0xDD));
    link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    link.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        openBugReportUrl();
      }
    });

    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    row.setOpaque(false);
    row.add(prefix);
    row.add(link);

    // "Console" and "Updates" are both painted directly onto a single
    // canvas component (drawString calls), not separate Swing children —
    // there's no way to insert a real component "between" them through
    // the normal layout, and adding a child directly into the tab bar
    // would fight its active BoxLayout. Instead we float our link on the
    // window's layered pane, positioned over the tab bar at the x-offset
    // where the "Console" tab text ends (computed the same way the
    // footer itself lays out tabs: LEFT_GUTTER + margin + text width +
    // margin), and keep it synced to the tab bar's on-screen position.
    JLabel versionLabel = findVersionLabel(ef);
    Container tabBar = (versionLabel != null) ? versionLabel.getParent() : null;
    if (tabBar == null) {
      // Fallback: append to the footer itself so it's at least visible.
      ef.add(row);
      ef.revalidate();
      ef.repaint();
      return;
    }

    Font tabFont = versionLabel.getFont();
    FontMetrics fm = tabBar.getFontMetrics(tabFont);
    final int LEFT_GUTTER = Editor.LEFT_GUTTER;
    final int MARGIN = 8; // matches EditorFooter's own tab margin
    int consoleTextWidth = fm.stringWidth("Console");
    int anchorX = LEFT_GUTTER + MARGIN + consoleTextWidth + MARGIN + 35;

    JRootPane rootPane = getRootPane();
    JLayeredPane layered = rootPane.getLayeredPane();
    Dimension pref = row.getPreferredSize();
    layered.add(row, JLayeredPane.PALETTE_LAYER);
    row.setVisible(false); // hidden until we confirm there's room

    final int SAFETY_GAP = 16; // minimum breathing room before Updates/version

    Runnable reposition = () -> {
      if (!tabBar.isShowing() || !versionLabel.isShowing()) {
        row.setVisible(false);
        return;
      }
      Point tabBarPos = SwingUtilities.convertPoint(tabBar, 0, 0, layered);
      Point versionPos = SwingUtilities.convertPoint(versionLabel, 0, 0, tabBar);

      // Only show the link if it fits entirely before the version label
      // (which also means before "Updates", since that's painted right up
      // against the version label's left edge) with some breathing room.
      boolean fits = (anchorX + pref.width + SAFETY_GAP) <= versionPos.x;
      row.setVisible(fits);
      if (!fits) return;

      int y = tabBarPos.y + (tabBar.getHeight() - pref.height) / 2;
      row.setBounds(tabBarPos.x + anchorX, Math.max(0, y), pref.width, pref.height);
    };
    reposition.run();

    java.awt.event.ComponentAdapter syncListener = new java.awt.event.ComponentAdapter() {
      @Override public void componentResized(java.awt.event.ComponentEvent e) { reposition.run(); }
      @Override public void componentMoved(java.awt.event.ComponentEvent e)   { reposition.run(); }
    };
    tabBar.addComponentListener(syncListener);
    rootPane.addComponentListener(syncListener);

    // Safety net: if the window wasn't fully shown yet on the first pass,
    // retry briefly until it is (covers the case where no resize/move ever
    // happens after this runs, so the link would otherwise stay at 0,0).
    Timer retry = new Timer(150, null);
    int[] attempts = { 0 };
    retry.addActionListener(e -> {
      reposition.run();
      attempts[0]++;
      if (tabBar.isShowing() || attempts[0] > 10) {
        retry.stop();
      }
    });
    retry.start();

    layered.revalidate();
    layered.repaint();
  }

  private boolean looksLikeVersionLabel(Component c) {
    if (!(c instanceof JLabel)) return false;
    String text = ((JLabel) c).getText();
    return text != null && text.matches("\\d+(\\.\\d+)+\\w*");
  }

  private JLabel findVersionLabel(Container parent) {
    for (Component c : parent.getComponents()) {
      if (looksLikeVersionLabel(c)) return (JLabel) c;
      if (c instanceof Container) {
        JLabel found = findVersionLabel((Container) c);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void openBugReportUrl() {
    // Try AWT Desktop first.
    try {
      if (java.awt.Desktop.isDesktopSupported()
          && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
        java.awt.Desktop.getDesktop().browse(new java.net.URI(BUG_REPORT_URL));
        return;
      }
    } catch (Exception ignored) {
      // fall through to OS-specific commands below
    }

    // Linux fallback: try common openers directly.
    String[] candidates = { "xdg-open", "gio", "gnome-open", "kde-open" };
    for (String cmd : candidates) {
      try {
        ProcessBuilder pb = (cmd.equals("gio"))
          ? new ProcessBuilder(cmd, "open", BUG_REPORT_URL)
          : new ProcessBuilder(cmd, BUG_REPORT_URL);
        pb.start();
        return;
      } catch (Exception ignored) {
        // try next candidate
      }
    }

    // Nothing worked — let the user copy the link themselves.
    JOptionPane.showMessageDialog(this,
      "Couldn't open the browser automatically.\n\nPlease visit:\n" + BUG_REPORT_URL,
      "Open Link", JOptionPane.INFORMATION_MESSAGE);
  }

  @Override
  protected JEditTextArea createTextArea() {
    PdeTextArea ta = new PdeTextArea(new PdeTextAreaDefaults(),
                                     new CppInputHandler(this), this);
    InputHandler ih = ta.getInputHandler();

    // NOTE: ENTER (auto-indent), TAB/S+TAB (indent/outdent/expand), and
    // C+SLASH (comment toggle) are now handled by CppInputHandler's
    // handlePressed(), mirroring JavaInputHandler, so they're no longer
    // bound here individually.

    // ── Home / End ─────────────────────────────────────────────────────────
    ih.addKeyBinding("HOME",    InputHandler.HOME);
    ih.addKeyBinding("END",     InputHandler.END);
    ih.addKeyBinding("S+HOME",  InputHandler.SELECT_HOME);
    ih.addKeyBinding("S+END",   InputHandler.SELECT_END);
    ih.addKeyBinding("C+HOME",  InputHandler.DOCUMENT_HOME);
    ih.addKeyBinding("C+END",   InputHandler.DOCUMENT_END);
    ih.addKeyBinding("CS+HOME", InputHandler.SELECT_DOC_HOME);
    ih.addKeyBinding("CS+END",  InputHandler.SELECT_DOC_END);

    // ── Arrow keys with selection ──────────────────────────────────────────
    ih.addKeyBinding("S+LEFT",  InputHandler.SELECT_PREV_CHAR);
    ih.addKeyBinding("S+RIGHT", InputHandler.SELECT_NEXT_CHAR);
    ih.addKeyBinding("S+UP",    InputHandler.SELECT_PREV_LINE);
    ih.addKeyBinding("S+DOWN",  InputHandler.SELECT_NEXT_LINE);

    // ── Word navigation ────────────────────────────────────────────────────
    ih.addKeyBinding("C+LEFT",  InputHandler.PREV_WORD);
    ih.addKeyBinding("C+RIGHT", InputHandler.NEXT_WORD);
    ih.addKeyBinding("CS+LEFT", InputHandler.SELECT_PREV_WORD);
    ih.addKeyBinding("CS+RIGHT",InputHandler.SELECT_NEXT_WORD);

    // ── Delete ────────────────────────────────────────────────────────────
    ih.addKeyBinding("DELETE",    InputHandler.DELETE);
    ih.addKeyBinding("BACK_SPACE",InputHandler.BACKSPACE);
    ih.addKeyBinding("C+BACK_SPACE", InputHandler.BACKSPACE_WORD);
    ih.addKeyBinding("C+DELETE",  InputHandler.DELETE_WORD);

    // ── Page up/down ──────────────────────────────────────────────────────
    ih.addKeyBinding("PAGE_UP",   InputHandler.PREV_PAGE);
    ih.addKeyBinding("PAGE_DOWN", InputHandler.NEXT_PAGE);
    ih.addKeyBinding("S+PAGE_UP", InputHandler.SELECT_PREV_PAGE);
    ih.addKeyBinding("S+PAGE_DOWN",InputHandler.SELECT_NEXT_PAGE);

    // ── Error overlay ─────────────────────────────────────────────────────
    overlay = new ErrorOverlay(ta);
    ta.getPainter().setLayout(null);
    ta.getPainter().add(overlay);
    ta.getPainter().addComponentListener(new java.awt.event.ComponentAdapter() {
      public void componentResized(java.awt.event.ComponentEvent e) {
        overlay.setBounds(0, 0,
          ta.getPainter().getWidth(), ta.getPainter().getHeight());
      }
    });
    overlay.setBounds(0, 0, 600, 400);

    return ta;
  }

  // ── Error overlay ─────────────────────────────────────────────────────────
  class ErrorOverlay extends JComponent {
    private final JEditTextArea ta;
    ErrorOverlay(JEditTextArea ta) { this.ta = ta; setOpaque(false); }

    @Override
    protected void paintComponent(Graphics g) {
      if (errorLines.isEmpty()) return;
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(new Color(0xFF, 0x40, 0x40, 200));
      g2.setStroke(new BasicStroke(1.5f));
      int lineH       = ta.getPainter().getFontMetrics().getHeight();
      int firstVisible = ta.getFirstLine();
      for (int docLine : errorLines) {
        int visLine = docLine - firstVisible;
        if (visLine < 0) continue;
        int y = visLine * lineH + lineH - 3;
        int w = ta.getPainter().getWidth();
        for (int x = 0; x < w - 6; x += 6) {
          g2.drawLine(x,   y,     x+3, y - 2);
          g2.drawLine(x+3, y - 2, x+6, y);
        }
      }
      g2.dispose();
    }
  }

  // ── Error highlighting ────────────────────────────────────────────────────
  public void highlightErrorLine(int line) {
    if (line <= 0) return;
    int docLine = line - 1;
    if (!(textarea instanceof PdeTextArea)) return;
    PdeTextArea pta = (PdeTextArea) textarea;
    try {
      if (docLine >= pta.getLineCount()) return;
      errorLines.clear();
      errorLines.add(docLine);
      pta.clearGutterText();
      pta.setGutterText(docLine, "▶");
      pta.scrollTo(docLine, 0);
      pta.select(pta.getLineStartOffset(docLine),
                 pta.getLineStopOffset(docLine) - 1);
      if (overlay != null) overlay.repaint();
    } catch (Exception ignored) {}
  }

  public void clearErrorHighlight() {
    errorLines.clear();
    if (textarea instanceof PdeTextArea)
      ((PdeTextArea) textarea).clearGutterText();
    if (overlay != null) overlay.repaint();
  }

  // ── Comment toggle ────────────────────────────────────────────────────────
  void toggleComment(JEditTextArea ta) {
    int selStart  = ta.getSelectionStart();
    int selEnd    = ta.getSelectionStop();
    int startLine = ta.getLineOfOffset(selStart);
    int endLine   = ta.getLineOfOffset(selEnd);
    if (endLine > startLine && ta.getLineStartOffset(endLine) == selEnd)
      endLine--;

    boolean allCommented = true;
    for (int i = startLine; i <= endLine; i++) {
      if (!getLineText(ta, i).stripLeading().startsWith("//")) {
        allCommented = false; break;
      }
    }
    for (int i = endLine; i >= startLine; i--) {
      String raw = getLineText(ta, i);
      if (allCommented)
        replaceLineText(ta, i, raw.replaceFirst("(\\s*)//\\s?", "$1"));
      else {
        int indent = 0;
        while (indent < raw.length() &&
               (raw.charAt(indent)==' ' || raw.charAt(indent)=='\t')) indent++;
        replaceLineText(ta, i,
          raw.substring(0, indent) + "// " + raw.substring(indent));
      }
    }
  }

  private String getLineText(JEditTextArea ta, int line) {
    try {
      int start = ta.getLineStartOffset(line);
      int end   = ta.getLineStopOffset(line) - 1;
      return ta.getDocument().getText(start, Math.max(0, end - start));
    } catch (BadLocationException e) { return ""; }
  }

  private void replaceLineText(JEditTextArea ta, int line, String text) {
    try {
      int start = ta.getLineStartOffset(line);
      int end   = ta.getLineStopOffset(line) - 1;
      ta.getDocument().remove(start, Math.max(0, end - start));
      ta.getDocument().insertString(start, text, null);
    } catch (BadLocationException e) {}
  }

  @Override public EditorToolbar createToolbar() { return new CppToolbar(this); }
  @Override public Formatter createFormatter()   { return null; }
  @Override public JMenu buildFileMenu() {
    javax.swing.JMenuItem exportApp = new javax.swing.JMenuItem("Export Application...");
    exportApp.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
      java.awt.event.KeyEvent.VK_E,
      java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
        | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
    exportApp.addActionListener(e -> handleExportApplication());
    return buildFileMenu(new javax.swing.JMenuItem[]{ exportApp });
  }
  @Override public JMenu buildHelpMenu()         { return new JMenu("Help"); }
  @Override public void handleImportLibrary(String name) {}
  @Override public String getCommentPrefix()     { return "//"; }
  @Override public void startIndeterminate()     { }
  @Override public void stopIndeterminate()      { }
  @Override public void statusHalt()             { }
  @Override public boolean isHalted()            { return currentRunner == null; }
  @Override public void deactivateRun()          { toolbar.deactivateRun(); }

  @Override
  public void internalCloseRunner() {
    if (currentRunner != null) { currentRunner.stop(); currentRunner = null; }
  }

  @Override
  public JMenu buildSketchMenu() {
    JMenuItem run  = processing.app.ui.Toolkit.newJMenuItem("Run", 'R');
    JMenuItem stop = new JMenuItem("Stop");
    run.addActionListener(e  -> handleRun(false, null, null));
    stop.addActionListener(e -> handleStop());
    return buildSketchMenu(new JMenuItem[] { run, stop });
  }

  public void handleRun(boolean present, Runnable stopCb, Runnable startCb) {
    clearErrorHighlight();
    prepareRun();
    statusNotice("Compiling...");
    new Thread(() -> {
      try {
        CppMode mode = (CppMode) getMode();
        currentRunner = mode.handleLaunch(sketch, this);
        if (currentRunner != null) {
          statusNotice("Running.");
          if (startCb != null) startCb.run();
        } else {
          statusError("Build failed — see console for errors.");
          deactivateRun();
        }
      } catch (InstallWizard.CancelledByUser e) {
        statusNotice("Cancelled.");
        deactivateRun();
      } catch (Exception e) {
        statusError(e.getMessage());
        deactivateRun();
      }
    }).start();
  }

  public void handleStop() {
    internalCloseRunner();
    deactivateRun();
    statusNotice("Stopped.");
  }
  public void handleExportApplication() {
    // Block export only for truly unsaved sketches (untitled, never saved)
    // Check if the sketch folder is inside a temp directory
    java.io.File sketchFolder = sketch.getFolder();
    boolean isTemp = sketchFolder == null
      || !sketchFolder.exists()
      || sketch.isUntitled();
    if (isTemp) {
      javax.swing.JOptionPane.showMessageDialog(null,
        "<html><b>Please save the sketch first.</b><br><br>" +
        "Use <b>File → Save As...</b> to give it a name,<br>" +
        "then try Export Application again.</html>",
        "Save Required", javax.swing.JOptionPane.WARNING_MESSAGE);
      return;
    }
    // Save if modified
    if (sketch.isModified()) {
      int choice = javax.swing.JOptionPane.showOptionDialog(null,
        "<html><b>Sketch has unsaved changes.</b><br><br>" +
        "Save before exporting?</html>",
        "Unsaved Changes",
        javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
        javax.swing.JOptionPane.WARNING_MESSAGE,
        null,
        new String[]{"Save & Export", "Export Anyway", "Cancel"},
        "Save & Export");
      if (choice == 2 || choice < 0) return;
      if (choice == 0) {
        try { sketch.save(); } catch (Exception e) {
          javax.swing.JOptionPane.showMessageDialog(null,
            "Could not save: " + e.getMessage(),
            "Save Failed", javax.swing.JOptionPane.ERROR_MESSAGE);
          return;
        }
      }
    }
    CppBuild build = new CppBuild(sketch, (CppMode) getMode());
    build.showExportDialog(sketch, this);
  }

}
