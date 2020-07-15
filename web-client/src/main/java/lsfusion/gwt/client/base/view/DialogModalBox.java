package lsfusion.gwt.client.base.view;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.DialogBox;
import lsfusion.gwt.client.base.GwtClientUtils;
import lsfusion.gwt.client.view.MainFrame;

// twin of a PopupDialogPanel
public class DialogModalBox extends DialogBox {

    public DialogModalBox() {
        super(false, true);
    }

    public DialogModalBox(Caption captionWidget) {
        super(false, true, captionWidget);
//
//        addCloseHandler(event -> {
//            MainFrame.setModalPopup(false);
//            if(focusedElement != null) {
//                focusedElement.focus();
//                focusedElement = null; // just in case because sometimes hide is called without show (and the same DialogModalBox is used several time)
//            }
//        });
    }

    private Element focusedElement;

    @Override
    public void show() {
        focusedElement = GwtClientUtils.getFocusedElement();
        MainFrame.setModalPopup(true);

        super.show();
    }

    @Override
    public void hide(boolean autoHide) {
        super.hide(autoHide);

        MainFrame.setModalPopup(false);
        if(focusedElement != null) {
            focusedElement.focus();
            focusedElement = null; // just in case because sometimes hide is called without show (and the same DialogModalBox is used several time)
        }
    }
}
