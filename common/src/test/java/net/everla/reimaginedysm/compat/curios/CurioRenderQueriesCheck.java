package net.everla.reimaginedysm.compat.curios;

import java.util.Set;

public final class CurioRenderQueriesCheck {
    public static void main(String[] args) {
        assert CurioRenderQueries.find("ysm.has_any_curios('head', 'goety:unholy_hat')")
                .equals(Set.of("goety:unholy_hat"));
        assert CurioRenderQueries.find("ysm.has_any_curios(slot(), 'goety_revelation:ascension_halo', 'goety_revelation:broken_halo')")
                .equals(Set.of("goety_revelation:ascension_halo", "goety_revelation:broken_halo"));
        assert CurioRenderQueries.find("ysm.has_any_curios_with_all_tags('head', 'example:halos') || ysm.has_any_curios_with_any_tag('head', 'example:headwear')")
                .equals(Set.of("#example:halos", "#example:headwear"));
        assert CurioRenderQueries.find("variable.description = 'goety:unholy_hat'; math.sin(1)").isEmpty();
        assert CurioRenderQueries.find("ysm.has_any_curios('head', 'goety:unholy_hat') && ysm.has_any_curios('head', 'goety_revelation:broken_halo')")
                .equals(Set.of("goety:unholy_hat", "goety_revelation:broken_halo"));
        assert CurioRenderQueries.find("ysm.has_any_curios('head', 'goety:unholy_hat'").isEmpty();
        System.out.println("Curio render query checks passed");
    }
}
