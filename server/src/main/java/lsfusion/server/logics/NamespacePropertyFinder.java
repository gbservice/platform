package lsfusion.server.logics;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.classes.sets.AndClassSet;
import lsfusion.server.logics.linear.LP;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by DAle on 10.04.14.
 */

public class NamespacePropertyFinder extends NamespaceElementFinder<LP<?, ?>, List<AndClassSet>> {

    public NamespacePropertyFinder(LogicsModule.ModuleFinder<LP<?, ?>, List<AndClassSet>> finder, List<LogicsModule> modules) {
        super(finder, modules);
    }

    @Override
    protected List<FoundItem<LP<?, ?>>> finalizeResult(List<FoundItem<LP<?, ?>>> result) {
        return filterFoundProperties(result);
    }
    
    static public List<FoundItem<LP<?, ?>>> filterFoundProperties(List<FoundItem<LP<?, ?>>> result) {
        int cnt = result.size();
        List<FoundItem<LP<?, ?>>> finalResult = new ArrayList<FoundItem<LP<?, ?>>>();
        for (int i = 0; i < cnt; i++) {
            LP<?, ?> iProp = result.get(i).value;
            List<AndClassSet> iParams = result.get(i).module.propClasses.get(iProp);
            boolean foundMoreSpecialized = false;
            for (int j = 0; j < cnt; j++) {
                LP<?, ?> jProp = result.get(j).value;
                if (i != j && LogicsModule.match(iParams, result.get(j).module.propClasses.get(jProp), false) && 
                              !LogicsModule.match(result.get(j).module.propClasses.get(jProp), iParams, false)) {
                    foundMoreSpecialized = true;
                    break;
                }
            }
            if (!foundMoreSpecialized) {
                finalResult.add(result.get(i));
            }
        }
        return finalResult;        
    }
}
