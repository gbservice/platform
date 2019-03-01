package lsfusion.http.provider.form;

import lsfusion.gwt.shared.view.GForm;
import lsfusion.interop.form.RemoteFormInterface;

import java.io.IOException;

public interface FormProvider {

    GForm createForm(String canonicalName, String formSID, RemoteFormInterface remoteForm, Object[] immutableMethods, byte[] firstChanges, String sessionID) throws IOException;

    FormSessionObject getFormSessionObject(String formSessionID);
    void removeFormSessionObject(String formSessionID);
    void removeFormSessionObjects(String sessionID);
}