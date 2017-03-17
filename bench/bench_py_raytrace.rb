Thread.abort_on_exception = true

unless defined?(Truffle)
  module Truffle
    module Array
      def self.set_strategy(ary, strategy)
        ary
      end
    end
    module Debug
      def self.array_storage(ary)
        ary.class.to_s
      end
    end
  end
end

AMBIENT=0.1

class Vector
   attr_accessor :x, :y, :z
   def initialize(x,y,z)
     @x = x
     @y = y
     @z = z
   end

   def dot(b)
     @x*b.x + @y*b.y + @z*b.z
   end

   def cross(b)
     Vector.new(@y*b.z-@z*b.y, @z*b.x-@x*b.z, @x*b.y-@y*b.x)
   end

   def magnitude
     Math.sqrt(@x*@x+@y*@y+@z*@z)
   end

   def normal
     mag = magnitude
     Vector.new(@x/mag, @y/mag, @z/mag)
   end

   def +(b)
     Vector.new(@x+b.x, @y+b.y, @z+b.z)
   end

   def -(b)
     Vector.new(@x-b.x, @y-b.y, @z-b.z)
   end

   def *(b)
     Vector.new(@x*b, @y*b, @z*b)
   end

   def to_s
     "Vector(#{@x}, #{@y}, #{@z})"
   end
end

class Sphere
   attr_accessor :c, :r, :col
   def initialize(center, radius, color)
     @c = center
     @r = radius
     @col = color
   end
   def to_s
     "Sphere(c=#{c}, r=#{r}, col=#{col})"
   end

   def intersection(l)
#     STDERR.puts "Sphere#intersection(self=#{self}, l=#{l})"
     q = l.d.dot(l.o - @c)**2 - (l.o - @c).dot(l.o - @c) + @r**2
#     STDERR.puts "q=#{q}"
     if q < 0
        return Intersection.new( Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
     else
#puts "q >= 0 "
        d = -l.d.dot(l.o - @c)
        d1 = d - Math.sqrt(q)
        d2 = d + Math.sqrt(q)
        if 0 < d1 and ( d1 < d2 or d2 < 0)
           return Intersection.new(l.o+l.d*d1, d1, normal(l.o+l.d*d1), self)
        elsif 0 < d2 and (d2 < d1 or d1 < 0)
           return Intersection.new(l.o+l.d*d2, d2, normal(l.o+l.d*d2), self)
        else
	   return Intersection.new( Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
	end
     end
   end
   def normal(b)
     (b - @c).normal
   end
end

class Plane
   attr_accessor :n, :p, :col
   def initialize(point, normal, color)
     @n = normal
     @p = point
     @col = color
   end

   def intersection(l)
     d = l.d.dot(@n)
     if d == 0
        return Intersection.new( Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
     else
        d = (@p - l.o).dot(@n) / d
	return Intersection.new(l.o+l.d*d, d, @n, self)
     end
   end
end

class Ray
   attr_accessor :o, :d
   def initialize(origin, direction)
     @o = origin
     @d = direction
   end
   def to_s
     "Ray(o=#{o}, d=#{d})"
   end
end

class Intersection
   attr_accessor :p, :d, :n, :obj
   def initialize(point, distance, normal, obj)
     @p = point
     @d = distance
     @n = normal
     @obj = obj
   end
end

def testRay(ray, objects, ignore=nil)
   intersect = Intersection.new( Vector.new(0,0,0), -1, Vector.new(0,0,0), nil)

   objects.each { |obj|
      if obj != ignore
	currentIntersect = obj.intersection(ray)
	if currentIntersect.d > 0 and intersect.d < 0
	  intersect = currentIntersect
	elsif 0 < currentIntersect.d and currentIntersect.d < intersect.d
	  intersect = currentIntersect
	end
      end
   }
   intersect
end

def trace(ray, objects, light, maxRecur)
  if maxRecur < 0
     return Vector.new(0,0,0)
  end
  intersect = testRay(ray, objects)
  if intersect.d == -1
    col = Vector.new(AMBIENT, AMBIENT, AMBIENT)
  elsif intersect.n.dot(light - intersect.p) < 0
    col = intersect.obj.col * AMBIENT
  else
    lightRay = Ray.new(intersect.p, (light-intersect.p).normal)
    if testRay(lightRay, objects, intersect.obj).d == -1
       lightIntensity = 1000.0/(4*Math::PI*(light-intersect.p).magnitude**2)
       col = intersect.obj.col * [intersect.n.normal.dot((light - intersect.p).normal*lightIntensity), AMBIENT].max
    else
       col = intersect.obj.col * AMBIENT
    end
  end
  col
end

def task(img, x, h, cameraPos, objs, lightSource)
#puts "in task h=#{h}"
  line = img[x]
#puts "line[0] = #{line[0]}"
  h.times { |y|
     ray = Ray.new(cameraPos, (Vector.new(x/50.0-5, y/50.0-5, 0)-cameraPos).normal)
     col = trace(ray, objs, lightSource, 10)
     line[y] = (col.x + col.y + col.z) / 3.0
  }
  img[x] = line
#  STDERR.puts "line=#{line.join(', ')}"
#puts "task done"
  x
end

def future_task(img, x, h, cameraPos, objs, lightSource)
  ret = Queue.new
  return [Proc.new { res=task(img, x, h, cameraPos, objs, lightSource); ret.push(res) }, ret]
end

$workq = Queue.new
$futures = []
def future_dispatcher(ths, *args)
  f=future_task(*args)
  $workq.push(f)
  $futures << f
end

def thread_pool(ths, queue)
  ths.times.map { |t|
    Thread.new {
puts "Started thread #{t}"
      begin
        while job = queue.pop(true)
#puts "poped job #{job}"
  	job[0].call
        end
      rescue ThreadError
        puts "thread #{t} done"
      end
    }
  }
end

def run(ths=2, w=1024,h=1024)
  objs = []
  objs << Sphere.new( Vector.new(-2,0,-10), 2, Vector.new(0,255,0))
  objs << Sphere.new( Vector.new(2,0,-10), 3.5, Vector.new(255, 0, 0))
  objs << Sphere.new( Vector.new(0, -4, -10), 3, Vector.new(0,0,255))
  objs << Plane.new( Vector.new(0,0,-12), Vector.new(0,0,1), Vector.new(255,255,255))
  lightSource = Vector.new(0,10,0)

  cameraPos = Vector.new(0,0,20)
  img = [[0.0] * h] * w
  Truffle::Array.set_strategy(img, :FixedSize)
  pool = thread_pool(ths, $workq)
  parallel_time = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  w.times { |x| 
    future_dispatcher(ths, img, x, h, cameraPos, objs, lightSource)
  }
  $futures.each { |f|
    f[1].pop
  }
  parallel_time = Process.clock_gettime(Process::CLOCK_MONOTONIC) - parallel_time

  # shtudown current pool
  pool.each(&:join)
  return parallel_time
end
  
time = run(Integer(ARGV[0]), Integer(ARGV[1]), Integer(ARGV[2]))
puts "Time: #{time}"
