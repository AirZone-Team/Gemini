package net.minecraft.client.searchtree;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FullTextSearchTree<T> extends IdSearchTree<T> {
    private final SearchTree<T> plainTextSearchTree;

    public FullTextSearchTree(Function<T, Stream<String>> nameGetter, Function<T, Stream<Identifier>> idGetter, List<T> contents) {
        super(idGetter, contents);
        this.plainTextSearchTree = SearchTree.plainText(contents, nameGetter);
    }

    @Override
    protected List<T> searchPlainText(String text) {
        return this.plainTextSearchTree.search(text);
    }

    @Override
    protected List<T> searchIdentifier(String namespace, String path) {
        List<T> namespaces = this.identifierSearchTree.searchNamespace(namespace);
        List<T> paths = this.identifierSearchTree.searchPath(path);
        List<T> names = this.plainTextSearchTree.search(path);
        Iterator<T> mergedPathsAndNames = new MergingUniqueIterator<>(paths.iterator(), names.iterator(), this.additionOrder);
        return ImmutableList.copyOf(new IntersectionIterator<>(namespaces.iterator(), mergedPathsAndNames, this.additionOrder));
    }
}
