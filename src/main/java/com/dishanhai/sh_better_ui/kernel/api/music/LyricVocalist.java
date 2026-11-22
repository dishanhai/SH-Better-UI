// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import java.util.HashMap;
import java.util.Map;

/** A vocalist or ensemble identified by TTML metadata. */
public record LyricVocalist(String id, String name, boolean ensemble) {
    public static final String TTML_METADATA = "http://www.w3.org/ns/ttml#metadata";

    /** Reads the definition table once per document. */
    public static Map<String, LyricVocalist> ttmlDefinitions(Element root) {
        Map<String, LyricVocalist> definitions = new HashMap<>();
        var agents = root.getElementsByTagNameNS(TTML_METADATA, "agent");
        for (int index = 0; index < agents.getLength(); index++) {
            Element agent = (Element) agents.item(index);
            String id = agent.getAttributeNS(XMLConstants.XML_NS_URI, "id").strip();
            if (id.isEmpty()) continue;
            var names = agent.getElementsByTagNameNS(TTML_METADATA, "name");
            String name = names.getLength() == 0 ? "" : names.item(0).getTextContent().strip();
            definitions.putIfAbsent(id, new LyricVocalist(id, name, "group".equals(agent.getAttribute("type"))));
        }
        return definitions;
    }

    /** Resolves an explicit or inherited TTML agent reference. Multiple references indicate an ensemble. */
    public static LyricVocalist fromTtml(Element paragraph, Map<String, LyricVocalist> definitions) {
        for (Node node = paragraph; node instanceof Element element; node = element.getParentNode()) {
            String ref = element.getAttributeNS(TTML_METADATA, "agent").strip();
            if (!ref.isEmpty()) {
                LyricVocalist defined = definitions.get(ref);
                if (defined != null) return defined;
                return new LyricVocalist(ref, "", ref.indexOf(' ') >= 0 || ref.indexOf('\t') >= 0
                        || "v1000".equals(ref));
            }
        }
        return null;
    }
}
