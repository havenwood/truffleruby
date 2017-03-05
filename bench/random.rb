# Random class, a "port" of the useful parts of java.util.Random
class Random 
    MULTIPLIER = 0x5DEECE66D
    ADDEND = 0xB
    MASK = (1 << 48) - 1;
    DOUBLE_UNIT = 1.0 / (1<<53).to_f 

    def initialize(seed=nil)
        @seedUniquifier = 8682522807148012
        @seed = (seed.nil?) ? seedUniquifier ^ Time.now.nsec : initialScramble(seed)
        @haveNextNextGaussian = false;
    end
    
    def seedUniquifier()  # this has no meaning if seedUniquifier isn't atomically accessed by multiple threads
        @seedUniquifier = @seedUniquifier * 181783497276652981
    end

    def initialScramble(seed)
        return (seed ^ MULTIPLIER) & MASK
    end

    def setSeed(seed)
        @seed = initialScramble(seed);
        @haveNextNextGaussian = false;
    end

    def next_(bits) 
        seed = @seed;
        oldseed = seed
        nextseed = (oldseed * MULTIPLIER + ADDEND) & MASK
        @seed =  nextseed
        return (nextseed >> (48 - bits)).to_i
    end


    def nextInt(bound=nil)
        return next_(32) if bound.nil?

        r=next_(31)
        m = bound - 1
        if ((bound & m) == 0)  # i.e., bound is a power of 2
            r = ((bound * r) >> 31).to_i
        else 
            u = r
	    while (u - (r = u % bound) + m < 0) do
                 u = next_(31)
            end    
        end
        return r
    end

    def next_int(bound) # for compatibility with MyRandom
       return nextInt(bound)
    end


    def nextLong
        return ((next_(32)) << 32) + next_(32)
    end

    def nextFloat 
        return next_(24).to_f / (1 << 24).to_f
    end

    def nextDouble 
        return (next_(26) << 27).to_f * DOUBLE_UNIT + next_(27) * DOUBLE_UNIT
    end

    def nextGaussian
        if (@haveNextNextGaussian) 
            @haveNextNextGaussian = false
            return @nextNextGaussian
        else
            begin 
                v1 = 2 * nextDouble() - 1; # between -1 and 1
                v2 = 2 * nextDouble() - 1; # between -1 and 1
                s = v1 * v1 + v2 * v2;
            end while (s >= 1 || s == 0)
            multiplier = Math.sqrt(-2 * Math.log(s)/s)
            @nextNextGaussian = v2 * multiplier;
            @haveNextNextGaussian = true;
            return v1 * multiplier;
        end
    end
end

