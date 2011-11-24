package platform.client.form;

import platform.interop.KeyStrokes;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventObject;

public abstract class ClientFormTable extends JTable implements TableTransferHandler.TableInterface {

    protected ClientFormTable() {
        this(null);
    }


    abstract protected boolean isEditOnSingleClick(int row, int column);

    protected ClientFormTable(TableModel model) {
        super(model);

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        setSurrendersFocusOnKeystroke(true);

        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        getTableHeader().setFocusable(false);
        getTableHeader().setReorderingAllowed(false);

        setupActionMap();

        setTransferHandler(new TableTransferHandler() {
            @Override
            protected TableInterface getTable() {
                return ClientFormTable.this;
            }
        });
    }

    private void setupActionMap() {
        //  Have the enter key work the same as the tab key
        InputMap im = getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStrokes.getEnter(), im.get(KeyStrokes.getTab()));
    }

    public boolean editCellAt(int row, int column, EventObject e){
        if (e instanceof MouseEvent) {
            // чтобы не срабатывало редактирование при изменении ряда,
            // потому что всё равно будет апдейт
            int selRow = getSelectedRow();
            if (selRow != -1 && selRow != row && !isEditOnSingleClick(row, column)) {
                return false;
            }
        }

        boolean result = super.editCellAt(row, column, e);
        if (result) {
            final Component editor = getEditorComponent();
            if (editor instanceof JTextComponent) {
                JTextComponent textEditor = (JTextComponent) editor;
                textEditor.selectAll();
                if (clearText(row, column, e)) {
                    textEditor.setText("");
                }
            }
        }

        return result;
    }

    public boolean clearText(int row, int column, EventObject e) {
       return false;
    }

    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        boolean consumed = super.processKeyBinding(ks, e, condition, pressed);
        // Вырежем кнопки фильтров, чтобы startEditing не поглощало его
        if (ks.equals(KeyStrokes.getFindKeyStroke(0))) return false;
        if (ks.equals(KeyStrokes.getFilterKeyStroke(0))) return false;
        //noinspection SimplifiableIfStatement
        if (ks.equals(KeyStrokes.getF8())) return false;

        return consumed;
    }
}
