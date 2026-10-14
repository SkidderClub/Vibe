"""Bounded TCS LZ2K decoder. Port of OpenSagaDev/nudat's MIT implementation.

See LICENSES/OpenSaga-nudat-MIT.txt and https://github.com/opensagadev/nudat.
"""


class Bits:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def get(self, n):
        if n < 0 or n > 24 or self.pos + n > len(self.data) * 8:
            raise ValueError('Truncated LZ2K bit stream')
        byte, bit = divmod(self.pos, 8)
        size = (bit + n + 7) // 8
        value = int.from_bytes(self.data[byte:byte + size], 'big')
        self.pos += n
        return (value >> (size * 8 - bit - n)) & ((1 << n) - 1) if n else 0

    def peek(self, n):
        pos = self.pos
        value = self.get(n)
        self.pos = pos
        return value


class Huffman:
    def __init__(self, lengths=None, constant=None):
        self.constant = constant
        self.table = {}
        if lengths is None:
            return
        counts = [lengths.count(i) for i in range(17)]
        codes = [0] * 17
        code = 0
        for i in range(1, 17):
            code = (code + (counts[i - 1] if i > 1 else 0)) << (1 if i > 1 else 0)
            if code + counts[i] > (1 << i):
                raise ValueError('Oversubscribed LZ2K Huffman tree')
            codes[i] = code
        if code + counts[16] != 65536:
            raise ValueError('Incomplete LZ2K Huffman tree')
        for symbol, size in enumerate(lengths):
            if size:
                self.table[size, codes[size]] = symbol
                codes[size] += 1

    def decode(self, bits):
        if self.constant is not None:
            return self.constant
        code = 0
        for size in range(1, 17):
            code = (code << 1) | bits.get(1)
            symbol = self.table.get((size, code))
            if symbol is not None:
                return symbol
        raise ValueError('Invalid LZ2K Huffman code')


def offsets(bits, size, count_bits, zero_after=None):
    n = bits.get(count_bits)
    if not n:
        return Huffman(constant=bits.get(count_bits))
    if n > size:
        raise ValueError('Invalid LZ2K offset table')
    lengths = [0] * size
    i = 0
    while i < n:
        value = bits.get(3)
        if value == 7:
            while bits.get(1):
                value += 1
                if value > 16:
                    raise ValueError('Invalid LZ2K code length')
        lengths[i] = value
        i += 1
        if i == zero_after:
            i += bits.get(2)
    return Huffman(lengths)


def literals(bits, code_lengths):
    n = bits.get(9)
    if not n:
        return Huffman(constant=bits.get(9))
    if n > 510:
        raise ValueError('Invalid LZ2K literal table')
    lengths = [0] * 510
    i = 0
    while i < n:
        value = code_lengths.decode(bits)
        if value < 3:
            i += 1 if value == 0 else bits.get(4) + 3 if value == 1 else bits.get(9) + 20
            if i > 510:
                raise ValueError('LZ2K literal table overflow')
        else:
            lengths[i] = value - 2
            i += 1
    return Huffman(lengths)


def decode(data, size):
    bits = Bits(data)
    out = bytearray()
    left = 0
    while len(out) < size:
        if not left:
            left = bits.get(16)
            if not left:
                raise ValueError('Empty LZ2K block')
            literal = literals(bits, offsets(bits, 19, 5, 3))
            offset = offsets(bits, 14, 4)
        left -= 1
        symbol = literal.decode(bits)
        if symbol < 256:
            out.append(symbol)
        else:
            code = offset.decode(bits)
            distance = 1 + ((1 << (code - 1)) + bits.get(code - 1) if code else 0)
            count = symbol - 253
            if distance > len(out) or len(out) + count > size:
                raise ValueError('Invalid LZ2K back reference')
            for _ in range(count):
                out.append(out[-distance])
    return bytes(out)
