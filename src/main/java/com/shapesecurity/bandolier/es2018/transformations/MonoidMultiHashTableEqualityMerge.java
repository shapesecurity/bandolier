package com.shapesecurity.bandolier.es2018.transformations;

import com.shapesecurity.functional.data.Monoid;
import com.shapesecurity.functional.data.MultiHashTable;

import javax.annotation.Nonnull;

public class MonoidMultiHashTableEqualityMerge<A, B> implements Monoid<MultiHashTable<A, B>> {
	@Nonnull
	@Override
	public MultiHashTable<A, B> identity() {
		return MultiHashTable.emptyUsingEquality();
	}

	@Nonnull
	@Override
	public MultiHashTable<A, B> append(MultiHashTable<A, B> t1, MultiHashTable<A, B> t2) {
		return t1.merge(t2);
	}
}
