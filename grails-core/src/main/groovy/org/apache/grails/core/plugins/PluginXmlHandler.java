package org.apache.grails.core.plugins;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler for parsing {@code META-INF/grails-plugin.xml} files.
 * Extracts {@code <type>} elements containing plugin class
 * fully-qualified names.
 */
class PluginXmlHandler extends DefaultHandler {

    private enum ParseState {IDLE, TYPE, RESOURCE}

    private ParseState state = ParseState.IDLE;
    private final List<String> pluginTypes = new ArrayList<>();
    private final List<String> pluginClasses = new ArrayList<>();
    private StringBuilder buffer = new StringBuilder();

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) {
        if ("type".equals(localName) || "type".equals(qName)) {
            state = ParseState.TYPE;
            buffer = new StringBuilder();
        } else if ("resource".equals(localName) || "resource".equals(qName)) {
            state = ParseState.RESOURCE;
            buffer = new StringBuilder();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (state == ParseState.TYPE || state == ParseState.RESOURCE) {
            buffer.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (state) {
            case TYPE:
                pluginTypes.add(buffer.toString().trim());
                break;
            case RESOURCE:
                pluginClasses.add(buffer.toString().trim());
                break;
            default:
                break;
        }
        state = ParseState.IDLE;
    }

    public List<String> getPluginTypes() {
        return pluginTypes;
    }

    public List<String> getPluginClasses() {
        return pluginClasses;
    }
}
