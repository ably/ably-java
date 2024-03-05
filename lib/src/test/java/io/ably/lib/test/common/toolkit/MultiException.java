package io.ably.lib.test.common.toolkit;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class MultiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final String EXCEPTION_SEPARATOR = "\n\t______________________________________________________________________\n";
    private final List<Throwable> nested = new ArrayList<>();

    public MultiException() {
        super("Multiple exceptions");
    }

    public void add(Throwable throwable) {
        if (throwable != null) {
            synchronized (nested) {
                if (throwable instanceof MultiException) {
                    MultiException other = (MultiException) throwable;
                    synchronized (other.nested) {
                        nested.addAll(other.nested);
                    }
                } else {
                    nested.add(throwable);
                }
            }
        }
    }

    /**
     * If this <code>MultiException</code> is empty then no action is taken,
     * if it contains a single <code>Throwable</code> that is thrown,
     * otherwise this <code>MultiException</code> is thrown.
     */
    public void throwIfNotEmpty() {
        synchronized (nested) {
            if (nested.isEmpty()) {
                // Do nothing
            } else if (nested.size() == 1) {
                Throwable t = nested.get(0);
                SneakyThrower.sneakyThrow(t);
            } else {
                throw this;
            }
        }
    }

    @Override
    public String getMessage() {
        synchronized (nested) {
            if (nested.isEmpty()) {
                return "<no nested exceptions>";
            } else {
                StringBuilder sb = new StringBuilder();
                int n = nested.size();
                sb.append(n).append(n == 1 ? " nested exception:" : " nested exceptions:");
                for (Throwable t : nested) {
                    sb.append(EXCEPTION_SEPARATOR).append("\n\t");
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    sb.append(sw.toString().replace("\n", "\n\t").trim());
                }
                sb.append(EXCEPTION_SEPARATOR);
                return sb.toString();
            }
        }
    }
}
