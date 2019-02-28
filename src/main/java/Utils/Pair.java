package Utils;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString
@RequiredArgsConstructor
public class Pair<T, U> {
    public final T first;
    public final U second;
}
