require_relative '../compat'
require_relative 'abstract_threading'

AMBIENT = 0.1

class Vector
  attr_reader :x, :y, :z

  def initialize(x,y,z)
    @x = x
    @y = y
    @z = z
  end

  def dot(b)
    @x*b.x + @y*b.y + @z*b.z
  end

  def cross(b)
    Vector.new(@y*b.z - @z*b.y, @z*b.x - @x*b.z, @x*b.y - @y*b.x)
  end

  def magnitude
    Math.sqrt(@x*@x + @y*@y + @z*@z)
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
  attr_reader :c, :r, :col

  def initialize(center, radius, color)
    @c = center
    @r = radius
    @col = color
  end

  def intersection(l)
    q = l.d.dot(l.o - @c)**2 - (l.o - @c).dot(l.o - @c) + @r**2
    if q < 0
      Intersection.new(Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
    else
      d = -l.d.dot(l.o - @c)
      d1 = d - Math.sqrt(q)
      d2 = d + Math.sqrt(q)
      if 0 < d1 and (d1 < d2 or d2 < 0)
        Intersection.new(l.o+l.d*d1, d1, normal(l.o+l.d*d1), self)
      elsif 0 < d2 and (d2 < d1 or d1 < 0)
        Intersection.new(l.o+l.d*d2, d2, normal(l.o+l.d*d2), self)
      else
        Intersection.new(Vector.new(0,0,0), -1, Vector.new(0,0,0), self)
      end
    end
  end

   def normal(b)
     (b - @c).normal
   end
end

class Plane
  attr_reader :n, :p, :col

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
      Intersection.new(l.o + l.d*d, d, @n, self)
    end
  end
end

class Ray
  attr_reader :o, :d

  def initialize(origin, direction)
    @o = origin
    @d = direction
  end
end

class Intersection
  attr_reader :p, :d, :n, :obj

  def initialize(point, distance, normal, obj)
    @p = point
    @d = distance
    @n = normal
    @obj = obj
  end
end

def test_ray(ray, objects, ignore=nil)
  intersect = Intersection.new(Vector.new(0,0,0), -1, Vector.new(0,0,0), nil)
  objects.each { |obj|
    if obj != ignore
      current = obj.intersection(ray)
      if current.d > 0 and intersect.d < 0
        intersect = current
      elsif 0 < current.d and current.d < intersect.d
        intersect = current
      end
    end
  }
  intersect
end

def trace(ray, objects, light, max_recur)
  return Vector.new(0,0,0) if max_recur < 0
  intersect = test_ray(ray, objects)
  if intersect.d == -1
    Vector.new(AMBIENT, AMBIENT, AMBIENT)
  elsif intersect.n.dot(light - intersect.p) < 0
    intersect.obj.col * AMBIENT
  else
    light_ray = Ray.new(intersect.p, (light-intersect.p).normal)
    if test_ray(light_ray, objects, intersect.obj).d == -1
      light_intensity = 1000.0 / (4*Math::PI*(light-intersect.p).magnitude**2)
      intersect.obj.col * [intersect.n.normal.dot((light - intersect.p).normal*light_intensity), AMBIENT].max
    else
      intersect.obj.col * AMBIENT
    end
  end
end

def task(image, x, h, camera_pos, objects, light_source)
  # puts "in task h=#{h}"
  line = image[x]
  # puts "line[0] = #{line[0]}"
  h.times { |y|
    ray = Ray.new(camera_pos, (Vector.new(x/50.0-5, y/50.0-5, 0) - camera_pos).normal)
    col = trace(ray, objects, light_source, 10)
    line[y] = (col.x + col.y + col.z) / 3.0
  }
  image[x] = line
  # STDERR.puts "line=#{line.join(', ')}"
  # puts "task done"
  x
end

def run(threads = 2, w = 1024, h = 1024)
  pool = ThreadPool.new(threads)

  10.times do
    objects = [
      Sphere.new(Vector.new(-2,0,-10), 2.0, Vector.new(0,255,0)),
      Sphere.new(Vector.new(2,0,-10), 3.5, Vector.new(255,0,0)),
      Sphere.new(Vector.new(0,-4,-10), 3.0, Vector.new(0,0,255)),
      Plane.new(Vector.new(0,0,-12), Vector.new(0,0,1), Vector.new(255,255,255)),
    ]
    light_source = Vector.new(0,10,0)
    camera_pos = Vector.new(0,0,20)

    image = Array.new(w) { Array.new(h, 0.0) }

    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    futures = Array.new(w) { |x|
      pool << Future.new {
        task(image, x, h, camera_pos, objects, light_source)
      }
    }
    futures.each(&:get)
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    dt = (t1-t0)
    puts dt

    # image.each { |row| puts row.inspect }
    p image.reduce(0) { |sum,row| sum + row.reduce(:+) }
    break
  end

  pool.shutdown
end
  
run(Integer(ARGV[0]), Integer(ARGV[1]), Integer(ARGV[2]))
