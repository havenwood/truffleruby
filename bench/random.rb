# Random class, a "port" of the useful parts of java.util.Random
class Random
  MULTIPLIER = 0x5DEECE66D
  ADDEND = 0xB
  MASK = (1 << 48) - 1
  DOUBLE_UNIT_LOW = 1.0 / (1<<53).to_f
  DOUBLE_UNIT_HIGH = 1.0 / (1<<26).to_f

  def initialize(seed=nil)
    @seedUniquifier = 8682522807148012
    @seed = (seed.nil?) ? ((seedUniquifier ^ Time.now.nsec) & MASK) : initialScramble(seed)
    @haveNextNextGaussian = false
  end

  def seedUniquifier()  # this has no meaning if seedUniquifier isn't atomically accessed by multiple threads
    @seedUniquifier = @seedUniquifier * 181783497276652981
  end

  def initialScramble(seed)
    (seed ^ MULTIPLIER) & MASK
  end

  def setSeed(seed)
    @seed = initialScramble(seed)
    @haveNextNextGaussian = false
  end

  def rand(bits)
    seed = @seed
    oldseed = seed
    nextseed = (oldseed * MULTIPLIER + ADDEND) & MASK
    @seed = nextseed
    (nextseed >> (48 - bits))
  end

  def next_int(bound = nil)
    return rand(32) unless bound

    r = rand(31)
    m = bound - 1
    if (bound & m) == 0  # i.e., bound is a power of 2
      ((bound * r) >> 31).to_i
    else
      u = r
      while u - (r = u % bound) + m < 0
        u = rand(31)
      end
      r
    end
  end

  def nextLong
    ((rand(32)) << 32) + rand(32)
  end

  def nextFloat
    rand(24).to_f / (1 << 24).to_f
  end

  def nextFloat
    next_(24).to_f / (1 << 24).to_f
  end

  def nextDouble
    next_(26) * DOUBLE_UNIT_HIGH + next_(27) * DOUBLE_UNIT_LOW
  end

  def nextGaussian
    if @haveNextNextGaussian
      @haveNextNextGaussian = false
      @nextNextGaussian
    else
      begin
        v1 = 2 * nextDouble() - 1 # between -1 and 1
        v2 = 2 * nextDouble() - 1 # between -1 and 1
        s = v1 * v1 + v2 * v2
      end while (s >= 1 || s == 0)
      multiplier = Math.sqrt(-2 * Math.log(s)/s)
      @nextNextGaussian = v2 * multiplier
      @haveNextNextGaussian = true
      v1 * multiplier
    end
  end
end
