package net.minecraft.client.searchtree;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IdSearchTree<T> implements SearchTree<T> {
    protected final Comparator<T> additionOrder;
    protected final IdentifierSearchTree<T> identifierSearchTree;

    public IdSearchTree(Function<T, Stream<Identifier>> idGetter, List<T> contents) {
        ToIntFunction<T> indexLookup = Util.createIndexLookup(contents);
        this.additionOrder = Comparator.comparingInt(indexLookup);
        this.identifierSearchTree = IdentifierSearchTree.create(contents, idGetter);
    }

    @Override
    public List<T> search(String text) {
        int colon = text.indexOf(58);
        return colon == -1 ? this.searchPlainText(text) : this.searchIdentifier(text.substring(0, colon).trim(), text.substring(colon + 1).trim());
    }

    protected List<T> searchPlainText(String text) {
        return this.identifierSearchTree.searchPath(text);
    }

    protected List<T> searchIdentifier(String namespace, String path) {
        List<T> namespaces = this.identifierSearchTree.searchNamespace(namespace);
        List<T> paths = this.identifierSearchTree.searchPath(path);
        return ImmutableList.copyOf(new IntersectionIterator<>(namespaces.iterator(), paths.iterator(), this.additionOrder));
    }
}
