package lsfusion.gwt.form.client.form.ui.layout;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;
import lsfusion.gwt.base.client.Dimension;
import lsfusion.gwt.base.client.GwtClientUtils;
import lsfusion.gwt.base.client.ui.FlexPanel;
import lsfusion.gwt.base.client.ui.GFlexAlignment;
import lsfusion.gwt.base.client.ui.HasPreferredSize;
import lsfusion.gwt.form.client.form.ui.layout.table.TableCaptionPanel;
import lsfusion.gwt.form.shared.view.GComponent;
import lsfusion.gwt.form.shared.view.GContainer;

import java.util.Map;

public class ScrollContainerView extends GAbstractContainerView {

    private final FlexPanel scrollPanel;
    private final boolean vertical = true;

    public ScrollContainerView(GContainer container) {
        super(container);

        assert container.isScroll();

        scrollPanel = new FlexPanel(vertical);

        view = scrollPanel;
    }

    private FlexPanel proxyPanel;
    private Widget proxyView;
    @Override
    protected void addImpl(int index, GComponent child, Widget view) {
        assert child.flex == 1 && child.alignment == GFlexAlignment.STRETCH; // временные assert'ы чтобы проверить обратную совместимость
        view.getElement().getStyle().setOverflowY(Style.Overflow.AUTO); // scroll
        if(child.preferredHeight == 1) { // panel тем же базисом и flex'ом (assert что 1)
            proxyPanel = new FlexPanel(vertical);

            proxyPanel.add(view, child.alignment, child.flex > 0 ? 1 : 0);

            proxyView = view;
            view = proxyPanel;
        }
        add(scrollPanel, view, 0, child.alignment, child.flex, child, vertical);
    }

    @Override
    protected void removeImpl(int index, GComponent child, Widget view) {
        scrollPanel.remove(view);
    }

    @Override
    public Widget getView() {
        return view;
    }

    public void updateLayout() {
        if(proxyPanel != null) {
//            if(proxyView instanceof FlexPanel) {
//                for(Widget child : ((FlexPanel)proxyView)) {
//                        int height = GwtClientUtils.calculatePreferredSize(child).height;
//                        if (height > 0) {
//                            ((FlexPanel)proxyView).setChildFlexBasis(child, height);
////                            child.setHeight(height + "px");
//                        }
//                }
//            } else
            proxyPanel.setChildFlexBasis(proxyView, GwtClientUtils.calculatePreferredSize(proxyView).height);
        }
    }

    @Override
    public Dimension getPreferredSize(Map<GContainer, GAbstractContainerView> containerViews) {
        return getChildrenStackSize(containerViews, true);
    }
}