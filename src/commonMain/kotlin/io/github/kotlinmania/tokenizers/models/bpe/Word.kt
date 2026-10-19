// port-lint: source models/bpe/word.rs
package io.github.kotlinmania.tokenizers.models.bpe

import io.github.kotlinmania.tokenizers.tokenizer.Offsets
import kotlin.random.Random

internal data class Merge(
    val pos: Int,
    val rank: UInt,
    val newId: UInt,
) : Comparable<Merge> {
    override fun compareTo(other: Merge): Int {
        val cmp = rank.compareTo(other.rank)
        return if (cmp != 0) cmp else pos.compareTo(other.pos)
    }
}

internal data class Symbol(
    var c: UInt,
    var prev: Int,
    var next: Int,
    var len: Int,
) {
    fun mergeWith(other: Symbol, newC: UInt) {
        c = newC
        len += other.len
        next = other.next
    }
}

internal class Word(
    val symbols: MutableList<Symbol> = mutableListOf(),
) {
    public fun add(c: UInt, byteLen: Int) {
        val (prev, next) =
            if (symbols.isNotEmpty()) {
                val last = symbols.last()
                val len = symbols.size
                last.next = len
                Pair(len - 1, -1)
            } else {
                Pair(-1, -1)
            }

        symbols.add(
            Symbol(
                c = c,
                prev = prev,
                next = next,
                len = byteLen,
            ),
        )
    }

    public fun merge(
        c1: UInt,
        c2: UInt,
        replacement: UInt,
        maxLength: Int,
    ): List<Pair<PairIds, Int>> {
        val changes = mutableListOf<Pair<PairIds, Int>>()
        var i = 0
        while (i < symbols.size) {
            if (symbols[i].c == c1 && i + 1 < symbols.size && symbols[i + 1].c == c2) {
                val first = symbols[i]
                val second = symbols[i + 1]

                val newS =
                    Symbol(
                        c = replacement,
                        prev = first.prev,
                        next = second.next,
                        len = first.len + second.len,
                    )

                if (i > 0) {
                    changes.add(Pair(PairIds(symbols[i - 1].c, first.c), -1))
                    if (symbols[i - 1].len + newS.len < maxLength) {
                        changes.add(Pair(PairIds(symbols[i - 1].c, replacement), 1))
                    }
                }

                symbols[i] = newS
                symbols.removeAt(i + 1)

                if (i < symbols.size - 1) {
                    changes.add(Pair(PairIds(second.c, symbols[i + 1].c), -1))
                    if (symbols[i + 1].len + newS.len < maxLength) {
                        changes.add(Pair(PairIds(replacement, symbols[i + 1].c), 1))
                    }
                }
            }
            i++
        }
        return changes
    }

    public fun mergeAll(merges: Map<PairIds, Pair<UInt, UInt>>, dropout: Float?) {
        val queue = SimpleMinHeap<Merge>()
        val skip = mutableListOf<Merge>()

        for (i in 0 until symbols.size - 1) {
            val pair = PairIds(symbols[i].c, symbols[i + 1].c)
            val m = merges[pair]
            if (m != null) {
                queue.push(
                    Merge(
                        pos = i,
                        rank = m.first,
                        newId = m.second,
                    ),
                )
            }
        }

        while (true) {
            val top = queue.pop() ?: break
            val drop =
                if (dropout != null) {
                    Random.nextFloat() < dropout
                } else {
                    false
                }

            if (drop) {
                skip.add(top)
            } else {
                for (s in skip) {
                    queue.push(s)
                }
                skip.clear()

                if (symbols[top.pos].len == 0) continue
                if (symbols[top.pos].next == -1) continue

                val nextPos = symbols[top.pos].next
                if (nextPos < 0 || nextPos >= symbols.size) continue
                val right = symbols[nextPos]

                val targetNewPair = PairIds(symbols[top.pos].c, right.c)
                val mergeEntry = merges[targetNewPair]
                if (mergeEntry == null || mergeEntry.second != top.newId) {
                    continue
                }

                symbols[top.pos].mergeWith(right, top.newId)
                symbols[nextPos].len = 0

                if (right.next > -1 && right.next < symbols.size) {
                    symbols[right.next].prev = top.pos
                }

                val current = symbols[top.pos]
                if (current.prev >= 0) {
                    val prev = current.prev
                    val prevSymbol = symbols[prev]
                    val newPair = PairIds(prevSymbol.c, current.c)
                    val m = merges[newPair]
                    if (m != null) {
                        queue.push(
                            Merge(
                                pos = prev,
                                rank = m.first,
                                newId = m.second,
                            ),
                        )
                    }
                }

                val next = current.next
                if (next in 0 until symbols.size) {
                    val nextSymbol = symbols[next]
                    val newPair = PairIds(current.c, nextSymbol.c)
                    val m = merges[newPair]
                    if (m != null) {
                        queue.push(
                            Merge(
                                pos = top.pos,
                                rank = m.first,
                                newId = m.second,
                            ),
                        )
                    }
                }
            }
        }

        symbols.removeAll { it.len == 0 }
    }

    public fun getChars(): List<UInt> = symbols.map { it.c }

    public fun getCharsIter(): List<UInt> = symbols.map { it.c }

    public fun getOffsetsIter(): List<Offsets> {
        var pos = 0
        return symbols.map { symbol ->
            val newPos = pos + symbol.len
            val offset = Offsets(pos, newPos)
            pos = newPos
            offset
        }
    }

    public fun clone(): Word =
        Word(symbols.map { Symbol(it.c, it.prev, it.next, it.len) }.toMutableList())

    public companion object {
        public fun new(): Word = Word()

        public fun withCapacity(capacity: Int): Word = Word(ArrayList(capacity))
    }
}

internal class SimpleMinHeap<T : Comparable<T>> {
    private val data = mutableListOf<T>()

    fun push(item: T) {
        data.add(item)
        var i = data.size - 1
        while (i > 0) {
            val parent = (i - 1) / 2
            if (data[i] < data[parent]) {
                val tmp = data[i]
                data[i] = data[parent]
                data[parent] = tmp
                i = parent
            } else {
                break
            }
        }
    }

    fun pop(): T? {
        if (data.isEmpty()) return null
        val root = data[0]
        val last = data.removeAt(data.size - 1)
        if (data.isNotEmpty()) {
            data[0] = last
            var i = 0
            val size = data.size
            while (true) {
                var smallest = i
                val left = 2 * i + 1
                val right = 2 * i + 2
                if (left < size && data[left] < data[smallest]) smallest = left
                if (right < size && data[right] < data[smallest]) smallest = right
                if (smallest != i) {
                    val tmp = data[i]
                    data[i] = data[smallest]
                    data[smallest] = tmp
                    i = smallest
                } else {
                    break
                }
            }
        }
        return root
    }
}
