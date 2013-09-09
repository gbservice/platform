package lsfusion.client.form.cell;

import lsfusion.client.form.PropertyRenderer;
import lsfusion.client.form.renderer.StringPropertyRenderer;
import lsfusion.client.logics.ClientPropertyDraw;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// приходится наследоваться от JComponent только для того, чтобы поддержать updateUI
public class ClientAbstractCellRenderer extends JComponent implements TableCellRenderer {

    private static final StringPropertyRenderer nullPropertyRenderer = new StringPropertyRenderer(null);

    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
        CellTableInterface cellTable = (CellTableInterface) table;

        ClientPropertyDraw property = cellTable.getProperty(row, column);

        PropertyRenderer currentComp;
        if (property != null) {
            currentComp = property.getRendererComponent();
            currentComp.setValue(value, isSelected, hasFocus);
        } else {
            currentComp = nullPropertyRenderer;
            currentComp.setValue("", isSelected, hasFocus);
        }

        if (cellTable.isSelected(row, column) && !hasFocus) {
            currentComp.paintAsSelected();
        }

        JComponent comp = currentComp.getComponent();
        if (comp instanceof JButton) {
            ((JButton) comp).setSelected(cellTable.isPressed(row, column));
        }

        Color backgroundColor = cellTable.getBackgroundColor(row, column);
        if (backgroundColor != null) {
            if (!hasFocus && !isSelected && !cellTable.isSelected(row, column)) {
                comp.setBackground(backgroundColor);
            } else {
                Color bgColor = comp.getBackground();
                comp.setBackground(new Color(backgroundColor.getRGB() & bgColor.getRGB()));
            }
        }

        Color foregroundColor = cellTable.getForegroundColor(row, column);
        if (foregroundColor != null) {
            comp.setForeground(foregroundColor);
        }
        
        if (property != null) {
            comp.setFont(property.design.getFont(table));
        }

        renderers.add(comp);

        return comp;
    }

    private final List<JComponent> renderers = new ArrayList<JComponent>();
    @Override
    public void updateUI() {
        for (JComponent comp : renderers) {
            comp.updateUI();
        }
    }
}
