require_relative '../compat'
require_relative 'abstract_threading'

AMBIENT = 0.1

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
end

class Sphere
  attr_accessor :c, :r, :col

  def initialize(center, radius, color)
    @c = center
    @r = radius
    @col = color
  end

  def intersection(l)
    q = l.d.dot(l.o - @c)**2 - (l.o - @c).dot(l.o - @c) + @r**2
    if q < 0
      Intersection.new( Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
    else
      d = -l.d.dot(l.o - @c)
      d1 = d - Math.sqrt(q)
      d2 = d + Math.sqrt(q)
      if 0 < d1 and ( d1 < d2 or d2 < 0)
        Intersection.new(l.o+l.d*d1, d1, normal(l.o+l.d*d1), self)
      elsif 0 < d2 and (d2 < d1 or d1 < 0)
        Intersection.new(l.o+l.d*d2, d2, normal(l.o+l.d*d2), self)
      else
        Intersection.new( Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
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
      Intersection.new(Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
    else
      d = (@p - l.o).dot(@n) / d
      Intersection.new(l.o+l.d*d, d, @n, self)
    end
  end
end

class Ray
  attr_accessor :o, :d

  def initialize(origin, direction)
    @o = origin
    @d = direction
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
  intersect = Intersection.new(Vector.new(0,0,0), -1, Vector.new(0,0,0), nil)
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

def task(image, x, h, camera_pos, objs, light_source)
  # puts "in task h=#{h}"
  line = image[x]
  # puts "line[0] = #{line[0]}"
  h.times { |y|
    ray = Ray.new(camera_pos, (Vector.new(x/50.0-5, y/50.0-5, 0) - camera_pos).normal)
    col = trace(ray, objs, light_source, 10)
    line[y] = (col.x + col.y + col.z) / 3.0
  }
  image[x] = line
  # STDERR.puts "line=#{line.join(', ')}"
  # puts "task done"
  x
end

def run(threads = 2, w = 1024, h = 1024)
  objs = [
    Sphere.new(Vector.new(-2,0,-10), 2, Vector.new(0,255,0)),
    Sphere.new(Vector.new(2,0,-10), 3.5, Vector.new(255,0,0)),
    Sphere.new(Vector.new(0,-4,-10), 3, Vector.new(0,0,255)),
    Plane.new(Vector.new(0,0,-12), Vector.new(0,0,1), Vector.new(255,255,255)),
  ]
  light_source = Vector.new(0,10,0)
  camera_pos = Vector.new(0,0,20)

  image = Array.new(w) { Array.new(h, 0.0) }

  pool = pool = ThreadPool.new(threads)

  t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  w.times.map { |x|
    pool << Future.new {
      task(image, x, h, camera_pos, objs, light_source)
    }
  }.each(&:get)
  t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  dt = (t1-t0)
  puts dt

  image.each { |row| puts row.inspect }

  pool.shutdown
end
  
run(Integer(ARGV[0]), Integer(ARGV[1]), Integer(ARGV[2]))
