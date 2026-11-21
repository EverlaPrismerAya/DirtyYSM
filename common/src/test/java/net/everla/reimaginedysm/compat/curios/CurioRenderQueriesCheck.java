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
        assert CurioRenderQueries.find("query.is_item_name_any('head', 'youkaishomecoming:reimu_hairband')")
                .equals(Set.of("youkaishomecoming:reimu_hairband"));
        assert CurioRenderQueries.find("q.is_item_name_any('HEAD', 'youkaishomecoming:marisa_hat', 'youkaishomecoming:reimu_hairband')")
                .equals(Set.of("youkaishomecoming:marisa_hat", "youkaishomecoming:reimu_hairband"));
        assert CurioRenderQueries.find("q.equipped_item_all_tags('head', 'example:headwear') || query.equipped_item_any_tag('head', 'example:hats')")
                .equals(Set.of("#example:headwear", "#example:hats"));
        assert CurioRenderQueries.find("q.is_item_name_any('mainhand', 'youkaishomecoming:marisa_hat') || q.equipped_item_any_tag('chest', 'example:hats')").isEmpty();
        assert CurioRenderQueries.find("q.is_item_name_any(slot(), item())").isEmpty(); // Recorded at evaluation time.
        assert CurioRenderQueries.find("q.is_item_name_any('head', 'youkaishomecoming:marisa_hat'").isEmpty();
        assert CurioRenderQueries.find("ysm.has_any_curios('head', 'youkaishomecoming:marisa_hat') || query.is_item_name_any('head', 'youkaishomecoming:reimu_hairband')")
                .equals(Set.of("youkaishomecoming:marisa_hat", "youkaishomecoming:reimu_hairband"));
        System.out.println("Curios and head-equipment render query checks passed");
    }
}
