package win.fantest.chatweblite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ChatDomBridgeTest {
    @Test
    public void escapesClipboardTextForSingleQuotedJavaScript() {
        String value = "a'b\\c\n\r\t\u2028\u2029";
        assertEquals(
                "a\\'b\\\\c\\n\\r\\t\\u2028\\u2029",
                ChatDomBridge.escapeForSingleQuotedJavaScript(value)
        );
    }

    @Test
    public void copyScriptTargetsRenderedAssistantResponses() {
        String script = ChatDomBridge.copyLastOutputScript();
        assertTrue(script.contains("interactive-item-container.interactive-response"));
        assertTrue(script.contains(".rendered-markdown"));
        assertTrue(script.contains("workbench.panel.chat"));
        assertTrue(script.contains("agent-sessions-workbench"));
    }

    @Test
    public void pasteScriptTargetsVsCodeChatInput() {
        String script = ChatDomBridge.pastePromptScript("hello 'world'");
        assertTrue(script.contains("interactive-input-part"));
        assertTrue(script.contains("monaco-editor[role=\\\"code\\\"]"));
        assertTrue(script.contains("textarea"));
        assertTrue(script.contains("native-edit-context"));
        assertTrue(script.contains("hello \\'world\\'"));
    }

    @Test
    public void pasteScriptUsesMonacoClipboardEventPath() {
        String script = ChatDomBridge.pastePromptScript("hello");
        assertTrue(script.contains("new DataTransfer()"));
        assertTrue(script.contains("setData('text/plain',text)"));
        assertTrue(script.contains("new ClipboardEvent('paste'"));
        assertTrue(script.contains("dispatchEvent(paste)"));
    }

    @Test
    public void sendScriptTargetsEnabledVsCodeSendAction() {
        String script = ChatDomBridge.sendPromptScript();
        assertTrue(script.contains("chat-execute-toolbar"));
        assertTrue(script.contains("action-item:not(.disabled)"));
        assertTrue(script.contains("codicon-arrow-up-compact"));
    }
}
