package com.elfmcys.yesstevemodel.model;

import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.resource.YSMClientMapper;
import com.elfmcys.yesstevemodel.resource.models.MainModelInfo;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import java.util.Map;

/** Small catalog entry, without geometry, animations, sounds or texture payloads. */
public class ModelCatalogMetadata {
    public RawYsmModel.RawMetadata metadata;
    public RawYsmModel.RawProperties properties;
    public RawYsmModel.RawFooter footer;
    public int bones, cubes, faces;
    public String[] textures;
    public Map<String, Map<String, String>> translations;

    public ServerModelInfo toModelInfo() {
        RawYsmModel raw = new RawYsmModel();
        if (metadata != null) raw.metadata = metadata;
        if (properties != null) raw.properties = properties;
        if (footer != null) raw.footer = footer;
        if (translations != null) translations.forEach((locale, values) ->
                raw.languageFiles.put(locale, new RawYsmModel.RawLanguageFile("", values)));
        return YSMClientMapper.buildModelInfo(raw, new MainModelInfo(bones, cubes, faces));
    }
}
