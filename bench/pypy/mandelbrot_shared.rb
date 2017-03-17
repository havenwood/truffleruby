require_relative 'abstract_threading'

def calculate(image, ar, ai, br, bi, width, height, base_row, max_iter=255)
  # p [ar, ai, br, bi, width, height, base_row, max_iter]
  imag_step = (bi - ai) / (height - 1)
  real_step = (br - ar) / (width - 1)
  # puts "real/width:%.13f, imag/height:%.13f" % [real_step, imag_step]

  height.times do |y|
    zi = ai + y * imag_step
    width.times do |x|
      zr = ar + x * real_step
      z = Complex(zr, zi)
      c = z
      i = 0
      while i < max_iter-1 and z.abs <= 2.0
        z = z * z + c
        i += 1
      end
      image[base_row+y][x] = i
    end
  end
end

def save_image(image, file = 'outrb.png')
  require 'chunky_png'
  png = ChunkyPNG::Image.new(image[0].size, image.size)

  image.each_with_index { |row, y|
    row.each_with_index { |c, x|
      png[x,y] = ChunkyPNG::Color.grayscale(c)
    }
  }
  png.save(file)
end

def save_to_file(image, file = 'outrb.txt')
  s = image.map(&:inspect).join("\n")
  File.write(file, s)
end

# NOTE: strips must be >> threads, because the middle stripes are more expensive than the border stripes
def run(threads = 2, stripes = 64, width = 4096, height = 4096)
  raise unless stripes >= threads
  pool = ThreadPool.new(threads)
  image = nil

  10.times do
    ar, ai = -2.0, -1.5
    br, bi = 1.0, 1.5

    image = Array.new(height) { Array.new(width, 0) }
    step = (bi - ai) / stripes
    ai = -1.5
    bi = ai + step
    chunk = height / stripes

    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    stripes.times.map { |i|
      pool << Future.new {
        calculate(image,
                  ar, ai + i * step,
                  br, bi + i * step,
                  width, chunk, i * chunk)
      }
    }.each(&:get)
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    dt = (t1-t0)
    puts dt
    puts image.reduce(0) { |sum,row| sum + row.reduce(:+) }
  end

  pool.shutdown
  image
end

image = run(Integer(ARGV[0]), Integer(ARGV[1]), Integer(ARGV[2]), Integer(ARGV[3]))
# save_to_file(image)
# save_image(image)
