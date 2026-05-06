package anordine.dao.simplifier.webflux.example.users;

import anordine.dao.simplifier.webflux.cursor.CursorPage;
import java.util.List;
import java.util.function.Function;

public record CursorPageResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext
) {

    static <E, T> CursorPageResponse<T> fromPage(CursorPage<E> page, Function<E, T> mapper) {
        return new CursorPageResponse<>(
                page.content().stream().map(mapper).toList(),
                page.nextCursor(),
                page.hasNext()
        );
    }
}
