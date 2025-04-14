package org.lebastudios.theroundtable.pluginaccounting;

import org.lebastudios.theroundtable.MainStageController;
import org.lebastudios.theroundtable.fxml2java.CompileFxml;
import org.lebastudios.theroundtable.plugins.IPlugin;
import org.lebastudios.theroundtable.ui.IconView;
import org.lebastudios.theroundtable.ui.LabeledIconButton;

import java.util.List;

@CompileFxml(
        directories = {
                "org/lebastudios/theroundtable/pluginaccounting"
        }
)
public class PluginAccounting implements IPlugin
{
    @Override
    public void initialize()
    {
    }

    @Override
    public List<LabeledIconButton> getHomeButtons()
    {
        LabeledIconButton button = new LabeledIconButton(
                "Accounting",
                new IconView("accounting.png"),
                _ ->
                {
                    MainStageController.getInstance().setCentralNode(new MainPaneController());
                }
        );

        return List.of(button);
    }
}
