package platform.server.logics.scripted;

import platform.base.ResourceList;
import platform.server.auth.User;
import platform.server.data.sql.DataAdapter;
import platform.server.logics.BusinessLogics;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class ScriptingBusinessLogics extends BusinessLogics<ScriptingBusinessLogics> {
    private final String name;
    private final List<String> scriptFilePaths;

    public ScriptingBusinessLogics(String name, DataAdapter iAdapter, int port, String paths) throws Exception {
        this(name, iAdapter, port, Arrays.asList(paths.split(";")));
    }

    public ScriptingBusinessLogics(String name, DataAdapter adapter, int port, List<String> paths) throws Exception {
        super(adapter, port);
        this.name = name;
        this.scriptFilePaths = paths;
    }

    @Override
    protected void createModules() throws IOException {
        super.createModules();

        for (String filePath : scriptFilePaths) {
            if (new File(filePath).isFile())
                addModule(new ScriptingLogicsModule(filePath, LM, this));
            else {
                Pattern pattern = Pattern.compile(".*" + filePath + ".*\\.lsf");
                Collection<String> list = ResourceList.getResources(pattern);
                for (String name : list) {
                    addModule(new ScriptingLogicsModule(name, LM, this));
                }
            }
        }
    }

    //private String modifySlashes(String regexp) {
    //    return regexp.replace("/", "\\\\");
    //}

    protected void initAuthentication() throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        User admin = addUser("admin", "fusion");
    }

    @Override
    public String getName() throws RemoteException {
        return name;
    }
}
