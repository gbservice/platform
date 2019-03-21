package lsfusion.gwt.shared.view.classes;

import lsfusion.gwt.client.ClientMessages;
import lsfusion.gwt.client.form.property.cell.controller.EditManager;
import lsfusion.gwt.client.form.property.cell.classes.controller.ColorGridCellEditor;
import lsfusion.gwt.client.form.property.cell.controller.GridCellEditor;
import lsfusion.gwt.client.form.property.cell.classes.ColorGridCellRenderer;
import lsfusion.gwt.client.form.property.cell.GridCellRenderer;
import lsfusion.gwt.shared.view.GFont;
import lsfusion.gwt.shared.view.GPropertyDraw;
import lsfusion.gwt.shared.view.GWidthStringProcessor;

import java.text.ParseException;

public class GColorType extends GDataType {
    public static GColorType instance = new GColorType();

    @Override
    public GridCellEditor createGridCellEditor(EditManager editManager, GPropertyDraw editProperty) {
        return new ColorGridCellEditor(editManager, editProperty);
    }

    @Override
    public GridCellRenderer createGridCellRenderer(GPropertyDraw property) {
        return new ColorGridCellRenderer();
    }

    @Override
    public int getDefaultWidth(GFont font, GPropertyDraw propertyDraw, GWidthStringProcessor widthStringProcessor) {
        return 40;
    }

    @Override
    public Object parseString(String s, String pattern) throws ParseException {
        throw new ParseException("Color class doesn't support conversion from string", 0);
    }

    @Override
    public String toString() {
        return ClientMessages.Instance.get().typeColorCaption();
    }
}
