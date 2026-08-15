package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

@Buildable
public record CollectionPolicies(
        @BuilderAdder("item") Collection<String> collection,
        @BuilderAdder("listItem") List<String> list,
        @BuilderAdder("sequenceItem") SequencedCollection<String> sequencedCollection,
        @BuilderAdder("setItem") Set<String> set,
        @BuilderAdder("sequencedSetItem") SequencedSet<String> sequencedSet,
        @BuilderAdder("sortedSetItem") SortedSet<String> sortedSet,
        @BuilderAdder("navigableSetItem") NavigableSet<String> navigableSet,
        @BuilderAdder("entry") Map<String, String> map,
        @BuilderAdder("sequencedEntry") SequencedMap<String, String> sequencedMap,
        @BuilderAdder("sortedEntry") SortedMap<String, String> sortedMap,
        @BuilderAdder("navigableEntry") NavigableMap<String, String> navigableMap
) {
}
