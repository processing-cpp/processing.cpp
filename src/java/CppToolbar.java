/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

package processing.mode.cpp;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import processing.app.ui.Editor;
import processing.app.ui.EditorButton;
import processing.app.ui.EditorToolbar;


public class CppToolbar extends EditorToolbar {

  private final CppEditor cppEditor;


  public CppToolbar(Editor editor) {
    super(editor);
    this.cppEditor = (CppEditor) editor;
  }


  @Override
  public void handleRun(int modifiers) {
    boolean present = (modifiers & ActionEvent.SHIFT_MASK) != 0;
    cppEditor.handleRun(present, null, null);
  }

  @Override
  public void handleStop() {
    cppEditor.handleStop();
  }


  @Override
  public List<EditorButton> createButtons() {
    List<EditorButton> buttons = new ArrayList<>();

    runButton = new EditorButton(this,
                                 "/lib/toolbar/run",
                                 "Run",
                                 "Run (Shift for fullscreen)") {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleRun(e.getModifiers());
      }
    };
    buttons.add(runButton);

    stopButton = new EditorButton(this,
                                  "/lib/toolbar/stop",
                                  "Stop") {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleStop();
      }
    };
    buttons.add(stopButton);

    return buttons;
  }
}
