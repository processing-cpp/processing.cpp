/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  CppInputHandler - C++-mode-specific handling of keys

  Ported from processing.mode.java.JavaInputHandler to give CppMode's
  editor full behavioral parity with the stock Java mode: identical
  auto-indent on Enter (including brace-depth lookback), identical
  auto-dedent when typing a closing brace, identical Tab/Shift+Tab
  indent/outdent/expand handling, and identical Ctrl+Up/Ctrl+Down
  jump-to-blank-line navigation.

  On top of that parity baseline, this also keeps CppMode's own
  Ctrl+/ comment-toggle binding.
*/

package processing.mode.cpp;

import java.awt.event.KeyEvent;
import java.util.Arrays;

import processing.app.Preferences;
import processing.app.Sketch;
import processing.app.syntax.JEditTextArea;
import processing.app.syntax.PdeInputHandler;
import processing.app.ui.Editor;


public class CppInputHandler extends PdeInputHandler {

  public CppInputHandler(Editor editor) {
    super(editor);
  }


  /**
   * Intercepts key pressed events for JEditTextArea.
   * <p/>
   * Called by JEditTextArea inside processKeyEvent(). Note that this
   * won't intercept actual characters, because those are fired on
   * keyTyped().
   * @return true if the event has been handled (to remove it from the queue)
   */
  public boolean handlePressed(KeyEvent event) {
    char c = event.getKeyChar();
    int code = event.getKeyCode();

    Sketch sketch = editor.getSketch();
    JEditTextArea textarea = editor.getTextArea();

    if (event.isMetaDown()) {
      return false;
    }

    if ((code == KeyEvent.VK_BACK_SPACE) || (code == KeyEvent.VK_TAB) ||
        (code == KeyEvent.VK_ENTER) || ((c >= 32) && (c < 128))) {
      sketch.setModified(true);
    }

    // ── Ctrl+/ : toggle line comment (CppMode addition, kept from before) ──
    if (event.isControlDown() && (code == KeyEvent.VK_SLASH)) {
      if (editor instanceof CppEditor) {
        ((CppEditor) editor).toggleComment(textarea);
      }
      event.consume();
      return true;
    }

    if ((code == KeyEvent.VK_UP) && event.isControlDown()) {
      // back up to the last empty line
      char[] contents = textarea.getText().toCharArray();
      int caretIndex = textarea.getCaretPosition();

      int index = calcLineStart(caretIndex - 1, contents);
      index -= 2;  // step over the newline
      boolean onlySpaces = true;
      while (index > 0) {
        if (contents[index] == 10) {
          if (onlySpaces) {
            index++;
            break;
          } else {
            onlySpaces = true;  // reset
          }
        } else if (contents[index] != ' ') {
          onlySpaces = false;
        }
        index--;
      }
      // if the first char, index will be -2
      if (index < 0) index = 0;

      if (event.isShiftDown()) {
        textarea.setSelectionStart(caretIndex);
        textarea.setSelectionEnd(index);
      } else {
        textarea.setCaretPosition(index);
      }
      event.consume();

    } else if ((code == KeyEvent.VK_DOWN) && event.isControlDown()) {
      char[] contents = textarea.getText().toCharArray();
      int caretIndex = textarea.getCaretPosition();

      int index = caretIndex;
      int lineStart = 0;
      boolean onlySpaces = false;  // don't count this line
      while (index < contents.length) {
        if (contents[index] == 10) {
          if (onlySpaces) {
            index = lineStart;  // this is it
            break;
          } else {
            lineStart = index + 1;
            onlySpaces = true;  // reset
          }
        } else if (contents[index] != ' ') {
          onlySpaces = false;
        }
        index++;
      }

      if (event.isShiftDown()) {
        textarea.setSelectionStart(caretIndex);
        textarea.setSelectionEnd(index);
      } else {
        textarea.setCaretPosition(index);
      }
      event.consume();

    } else if (c == 9) {  // Tab
      if (event.isShiftDown()) {
        // if shift is down, the user always expects an outdent
        editor.handleOutdent();

      } else if (textarea.isSelectionActive()) {
        editor.handleIndent();

      } else if (Preferences.getBoolean("editor.tabs.expand")) {
        int tabSize = Preferences.getInteger("editor.tabs.size");
        textarea.setSelectedText(spaces(tabSize));
        event.consume();

      } else {  // !Preferences.getBoolean("editor.tabs.expand")
        textarea.setSelectedText("\t");
        event.consume();
      }

    } else if (code == 10 || code == 13) {  // auto-indent on Enter
      if (Preferences.getBoolean("editor.indent")) {
        char[] contents = textarea.getText().toCharArray();
        int tabSize = Preferences.getInteger("editor.tabs.size");

        // this is the previous character (i.e. when you hit return,
        // it'll be the last character just before where the newline
        // will be inserted)
        int origIndex = textarea.getCaretPosition() - 1;

        // calculate the amount of indent on the previous line;
        // this will be used *only if the prev line is not a brace*
        int spaceCount = calcSpaceCount(origIndex, contents);

        // If the last character was a left curly brace, indent one
        // level deeper than that brace's own line, walking back past
        // any intermediate lines.
        int index2 = origIndex;
        while ((index2 >= 0) && Character.isWhitespace(contents[index2])) {
          index2--;
        }
        if (index2 != -1) {
          if (contents[index2] == '{') {
            spaceCount = calcSpaceCount(index2, contents);
            spaceCount += tabSize;
          }
        }

        // walk forward from the caret to count spaces already there
        // so they aren't duplicated, and check for closing braces
        // ahead on the line
        int index = origIndex + 1;
        int extraCount = 0;
        while ((index < contents.length) && (contents[index] == ' ')) {
          extraCount++;
          index++;
        }
        int braceCount = 0;
        while ((index < contents.length) && (contents[index] != '\n')) {
          if (contents[index] == '}') {
            braceCount++;
          }
          index++;
        }

        spaceCount -= extraCount;

        if (spaceCount < 0) {
          textarea.setSelectionEnd(textarea.getSelectionStop() - spaceCount);
          textarea.setSelectedText("\n");
          textarea.setCaretPosition(textarea.getCaretPosition() + extraCount + spaceCount);
        } else {
          String insertion = "\n" + spaces(spaceCount);
          textarea.setSelectedText(insertion);
          textarea.setCaretPosition(textarea.getCaretPosition() + extraCount);
        }

        // not gonna bother handling more than one brace
        if (braceCount > 0) {
          int sel = textarea.getSelectionStart();
          if (sel - tabSize >= 0) {
            textarea.select(sel - tabSize, sel);
            String s = spaces(tabSize);
            if (s.equals(textarea.getSelectedText())) {
              textarea.setSelectedText("");
            } else {
              textarea.select(sel, sel);
            }
          }
        }
      } else {
        // Enter/Return was being consumed somehow even if false was
        // returned, so this is a band-aid to simply fire the event again.
        textarea.setSelectedText(String.valueOf(c));
      }
      // mark this event as already handled (all but ignored)
      event.consume();

    } else if (c == '}') {
      if (Preferences.getBoolean("editor.indent")) {
        // first remove anything that was there (in case multiple
        // characters are selected, so it's not in the way of the
        // spaces for the auto-indent)
        if (textarea.getSelectionStart() != textarea.getSelectionStop()) {
          textarea.setSelectedText("");
        }

        // if this brace is the only thing on the line, outdent
        char[] contents = textarea.getText().toCharArray();
        int prevCharIndex = textarea.getCaretPosition() - 1;

        // backup from the current caret position to the last newline,
        // checking for anything besides whitespace along the way.
        // if there's something besides whitespace, exit without
        // messing any sort of indenting.
        int index = prevCharIndex;
        boolean finished = false;
        while ((index != -1) && (!finished)) {
          if (contents[index] == 10) {
            finished = true;
            index++;
          } else if (contents[index] != ' ') {
            // don't do anything, this line has other stuff on it
            return false;
          } else {
            index--;
          }
        }
        if (!finished) return false;  // brace with no start
        int lineStartIndex = index;

        int pairedSpaceCount = calcBraceIndent(prevCharIndex, contents);
        if (pairedSpaceCount == -1) return false;

        textarea.setSelectionStart(lineStartIndex);
        textarea.setSelectedText(spaces(pairedSpaceCount));

        // mark this event as already handled
        event.consume();
        return true;
      }
    }
    return false;
  }


  public boolean handleTyped(KeyEvent event) {
    char c = event.getKeyChar();

    if (event.isControlDown()) {
      if (c == KeyEvent.VK_COMMA) {
        event.consume();
        return true;
      }
      if (c == KeyEvent.VK_SPACE) {
        event.consume();
        return true;
      }
    }
    return false;
  }


  /**
   * Return the index for the first character on this line.
   */
  protected int calcLineStart(int index, char[] contents) {
    boolean finished = false;
    while ((index != -1) && (!finished)) {
      if ((contents[index] == 10) || (contents[index] == 13)) {
        finished = true;
      } else {
        index--;
      }
    }
    // add one because index is either -1 (the start of the document)
    // or it's the newline character for the previous line
    return index + 1;
  }


  /**
   * Calculate the number of spaces on this line.
   */
  protected int calcSpaceCount(int index, char[] contents) {
    index = calcLineStart(index, contents);

    int spaceCount = 0;
    while ((index < contents.length) && (index >= 0) &&
           (contents[index++] == ' ')) {
      spaceCount++;
    }
    return spaceCount;
  }


  /**
   * Walk back from 'index' until the brace that seems to be
   * the beginning of the current block, and return the number of
   * spaces found on that line.
   */
  protected int calcBraceIndent(int index, char[] contents) {
    int braceDepth = 1;
    boolean finished = false;
    while ((index != -1) && (!finished)) {
      if (contents[index] == '}') {
        braceDepth++;
        index--;
      } else if (contents[index] == '{') {
        braceDepth--;
        if (braceDepth == 0) {
          finished = true;
        }
        index--;
      } else {
        index--;
      }
    }
    // never found a proper brace, be safe and don't do anything
    if (!finished) return -1;

    return calcSpaceCount(index, contents);
  }


  static private String spaces(int count) {
    char[] c = new char[count];
    Arrays.fill(c, ' ');
    return new String(c);
  }
}
