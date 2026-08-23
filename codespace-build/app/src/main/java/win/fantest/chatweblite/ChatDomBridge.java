package win.fantest.chatweblite;

final class ChatDomBridge {
    private static final String PANEL_ROOT = "div[id=\\\"workbench.panel.chat\\\"]";
    private static final String EDITOR_ROOT = ".editor-instance .interactive-session";
    private static final String AGENTS_ROOT =
            ".agent-sessions-workbench .session-view.is-active .interactive-session";

    private static final String RESPONSE_SELECTOR =
            PANEL_ROOT + " .interactive-item-container.interactive-response," +
            EDITOR_ROOT + " .interactive-item-container.interactive-response," +
            AGENTS_ROOT + " .interactive-item-container.interactive-response";

    private static final String INPUT_EDITOR_SELECTOR =
            PANEL_ROOT + " .interactive-input-part .monaco-editor[role=\\\"code\\\"]," +
            EDITOR_ROOT + " .interactive-input-part .monaco-editor[role=\\\"code\\\"]," +
            AGENTS_ROOT + " .interactive-input-part .monaco-editor[role=\\\"code\\\"]";

    private static final String ROOT_SELECTOR =
            PANEL_ROOT + "," + EDITOR_ROOT + "," + AGENTS_ROOT;

    private static final String SEND_SELECTOR =
            ".chat-input-toolbars > .chat-execute-toolbar .monaco-action-bar " +
            ".action-item:not(.disabled) > .action-label.codicon-arrow-up-compact";

    private static final String VISIBLE_FUNCTION =
            "function vis(e){if(!e)return false;" +
                    "var r=e.getBoundingClientRect();var s=getComputedStyle(e);" +
                    "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}";

    private ChatDomBridge() {
    }

    static String escapeForSingleQuotedJavaScript(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '\'':
                    out.append("\\'");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\u2028':
                    out.append("\\u2028");
                    break;
                case '\u2029':
                    out.append("\\u2029");
                    break;
                default:
                    out.append(c);
                    break;
            }
        }
        return out.toString();
    }

    static String copyLastOutputScript() {
        return "(function(){try{" + VISIBLE_FUNCTION +
                "var items=Array.from(document.querySelectorAll('" + RESPONSE_SELECTOR + "')).filter(vis);" +
                "if(!items.length)return '';" +
                "var last=items[items.length-1];" +
                "var parts=Array.from(last.querySelectorAll('.rendered-markdown'))" +
                ".map(function(e){return (e.innerText||e.textContent||'').trim();})" +
                ".filter(function(t){return t.length>0;});" +
                "var text=parts.length?parts.join('\\n\\n'):(last.innerText||last.textContent||'').trim();" +
                "return text;}catch(e){return '';}})();";
    }

    static String pastePromptScript(String clipboardText) {
        String escaped = escapeForSingleQuotedJavaScript(clipboardText);
        return "(function(){try{" + VISIBLE_FUNCTION +
                "var editors=Array.from(document.querySelectorAll('" + INPUT_EDITOR_SELECTOR + "')).filter(vis);" +
                "if(!editors.length)return 'input-missing';" +
                "var editor=editors[editors.length-1];" +
                "var input=editor.querySelector('.native-edit-context,textarea.inputarea,textarea');" +
                "if(!input)return 'input-missing';" +
                "var text='" + escaped + "';if(!text.length)return 'empty';" +
                "if(typeof DataTransfer==='undefined'||typeof ClipboardEvent==='undefined')return 'paste-unsupported';" +
                "input.focus();" +
                "var dt=new DataTransfer();dt.setData('text/plain',text);" +
                "var paste=new ClipboardEvent('paste',{clipboardData:dt,bubbles:true,cancelable:true});" +
                "input.dispatchEvent(paste);" +
                "return 'inserted';}catch(e){return 'input-missing';}})();";
    }

    static String sendPromptScript() {
        return "(function(){try{" + VISIBLE_FUNCTION +
                "var editors=Array.from(document.querySelectorAll('" + INPUT_EDITOR_SELECTOR + "')).filter(vis);" +
                "if(!editors.length)return 'input-missing';" +
                "var editor=editors[editors.length-1];" +
                "var root=editor.closest('" + ROOT_SELECTOR + "');" +
                "if(!root)return 'input-missing';" +
                "var send=root.querySelector('" + SEND_SELECTOR + "');" +
                "if(!send||!vis(send))return 'send-not-ready';" +
                "send.click();return 'sent';}catch(e){return 'send-not-ready';}})();";
    }
}
