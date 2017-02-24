// cc -std=gnu99 -O3 monte_carlo_pi.c -pthread -lrt -o monte_carlo_pi

//#define _POSIX_C_SOURCE 199309L
#define _XOPEN_SOURCE 700
#define _POSIX_BARRIERS 1

#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include <pthread.h>

#define MYCLOCK CLOCK_MONOTONIC

double randf(unsigned int *rng) {
  return (rand_r(rng) / (double)(RAND_MAX));
}

double estimate_pi(int samples) {
  int inside = 0;
  unsigned int rng = 0;
  for (int i = 0; i < samples; ++i) {
    double x = randf(&rng);
    double y = randf(&rng);
    if (x*x + y*y <= 1.0) {
      inside += 1;
    }
  }
  return (double)inside / (double)samples;
}

int T = 1;
int N = 40000000;
int iterations = 10;

pthread_barrier_t barrier;
double* volatile results;

void* work(void *input) {
  int t = (int)(long)(input);

  for (int i = 0; i < iterations; ++i) {
    pthread_barrier_wait(&barrier);
    double result = estimate_pi(N / T);
    results[t] = result;
    pthread_barrier_wait(&barrier);
  }

  return (void*)results;
}

int main(int argc, char const *argv[])
{
  srand(time(NULL));

  if (argc > 1) {
    T = atoi(argv[1]);
  }
  if (argc > 2) {
    N = atoi(argv[2]);
  }
  printf("%d threads, pi with %d samples\n", T, N);

  pthread_t threads[T];
  results = calloc(T, sizeof(double));
  pthread_barrier_init(&barrier, NULL, T+1);

  for (int i = 0; i < T; ++i) {
    pthread_create(&threads[i], NULL, work, (void*)(long)i);
  }

  for (int i = 0; i < iterations; ++i) {
    struct timespec t0, t1;
    pthread_barrier_wait(&barrier);
    clock_gettime(MYCLOCK, &t0);

    pthread_barrier_wait(&barrier);
    clock_gettime(MYCLOCK, &t1);

    long diffns = (t1.tv_sec - t0.tv_sec) * 1000000000 + (t1.tv_nsec - t0.tv_nsec);
    long diffms = diffns / 1000000;
    double pi = 0.0;
    for (int t = 0; t < T; ++t) {
      pi += results[t];
    }
    printf("pi ~= %.6f; Took %ld ms\n", pi / T * 4, diffms);
  }

  for (int i = 0; i < T; ++i) {
    pthread_join(threads[i], NULL);
  }

  return 0;
}


