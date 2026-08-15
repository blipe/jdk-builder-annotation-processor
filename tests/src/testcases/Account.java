package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAccessor;
import io.github.jdkbuilder.BuilderConstructor;
import io.github.jdkbuilder.BuilderRequired;

@Buildable
public final class Account {
    private final String accountId;
    private final boolean active;
    private final long balance;

    public Account() {
        this("default", false, 0L);
    }

    @BuilderConstructor
    public Account(
            @BuilderRequired @BuilderAccessor("id") String accountId,
            boolean active,
            long balance
    ) {
        this.accountId = accountId;
        this.active = active;
        this.balance = balance;
    }

    public String id() {
        return accountId;
    }

    public boolean isActive() {
        return active;
    }

    public long getBalance() {
        return balance;
    }
}
