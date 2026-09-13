import dev.pageh.helper.ReadingWindows;

public final class ReadingWindowsTest {
    public static void main(String[] args) {
        ReadingWindows windows = new ReadingWindows();
        windows.remember(10, "reader", "ReaderActivity");
        require(windows.matches(10,10,"reader","ReaderActivity"), "initial reader");
        require(!windows.matches(11,11,"reader","ReaderActivity"), "unidentified same-package dialog blocked");
        require(!windows.matches(10,11,"reader","ReaderActivity"), "underlying root while dialog focused blocked");
        windows.remove(11);
        require(windows.matches(10,10,"reader","ReaderActivity"), "dialog dismissal restores without activity event");
        windows.remember(12,"reader","RechargeActivity");
        require(!windows.matches(12,12,"reader","ReaderActivity"), "recharge Activity blocked");
        windows.remove(12);
        require(windows.matches(10,10,"reader","ReaderActivity"), "return from recharge restores old window");
        require(!windows.matches(10,99,"reader","ReaderActivity"), "system window focus blocked");
        require(windows.matches(10,10,"reader","ReaderActivity"), "system window dismissed");
        windows.remember(10,"reader","BookshelfActivity");
        require(!windows.matches(10,10,"reader","ReaderActivity"), "same window replaced by bookshelf blocked");
        windows.remove(10);
        require(!windows.matches(10,10,"reader","ReaderActivity"), "removed window not reused");
        require(!windows.matches(-1,-1,"reader","ReaderActivity"), "unknown windows blocked");
        System.out.println("PASS: 11 popup/focus/window-lifecycle regression cases");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
