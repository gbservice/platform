/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package platformlocal;

import com.toedter.calendar.JCalendar;

import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.util.HashSet;
import java.util.Set;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class SwingUtils {
    
    static void addFocusTraversalKey(Component comp, int id, KeyStroke key) {
        
        Set keys = comp.getFocusTraversalKeys(id);
        Set newKeys = new HashSet(keys);
        newKeys.add(key);
        comp.setFocusTraversalKeys(id, newKeys);
    }

    public static void removeFocusable(Container container) {

        container.setFocusable(false);
        for (Component comp : container.getComponents()) {
            comp.setFocusable(false);
            if (comp instanceof Container) {
                removeFocusable((Container)comp);
            }
        }
    }
}

class ExpandingTreeNode extends DefaultMutableTreeNode {

    public String toString() { return "Retreiving data..."; }
}

class ClientFormTable extends JTable {

    public ClientFormTable() {
        super();

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        setSurrendersFocusOnKeystroke(true);

        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        getTableHeader().setFocusable(false);
        getTableHeader().setReorderingAllowed(false);
    }

}

class SingleCellTable extends ClientFormTable {

    public SingleCellTable() {
        super();

        addFocusListener(new FocusListener() {

            public void focusGained(FocusEvent e) {
                requestFocusInWindow();
                changeSelection(0, 0, false, false);
            }

            public void focusLost(FocusEvent e) {
                getSelectionModel().clearSelection();
            }

        });

        SwingUtils.addFocusTraversalKey(this,
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));

/*        SwingUtils.addFocusTraversalKey(this,
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));*/

        SwingUtils.addFocusTraversalKey(this,
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));

   }

    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e,
					int condition, boolean pressed) {

        // сами обрабатываем нажатие клавиши Enter
        if (e.getKeyCode() == KeyEvent.VK_ENTER && pressed) {
            if (isEditing()) getCellEditor().stopCellEditing();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent();
            return true;
        } else
            return super.processKeyBinding(ks, e, condition, pressed);    

    }

}


class FocusOwnerTracer implements PropertyChangeListener {

    public static final String FOCUS_OWNER_PROPERTY = "permanentFocusOwner";
    protected KeyboardFocusManager focusManager;

    protected static void installFocusTracer() {
        KeyboardFocusManager focusManager = KeyboardFocusManager.
            getCurrentKeyboardFocusManager();
        new FocusOwnerTracer(focusManager);
    }

    public FocusOwnerTracer(KeyboardFocusManager focusManager) {
        this.focusManager = focusManager;
        startListening();
    }

    public void startListening() {
        if (focusManager != null) {
            focusManager.addPropertyChangeListener(FOCUS_OWNER_PROPERTY, this);
        }
    }

    public void stopListening() {
        if (focusManager != null) {
            focusManager.removePropertyChangeListener(FOCUS_OWNER_PROPERTY, this);
        }
    }

    public void propertyChange(PropertyChangeEvent e) {
        Component oldOwner = (Component) e.getOldValue();
        Component newOwner = (Component) e.getNewValue();
        System.out.print("focusOwner changed: ");
        System.out.print(" old - " + oldOwner);
        System.out.println(" new - " + newOwner);
    }

}