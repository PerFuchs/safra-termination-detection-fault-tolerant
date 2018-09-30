import copy
import sys

from read_results import get_configurations


def main():
  experiment_folder = sys.argv[1]

  summary = sys.argv[2] == "summary"

  configurations = get_configurations(experiment_folder)

  expected_configurations = {
    (25, '0'): 10,
    (50, '5n'): 4,
    (50, '0'): 4
  }

  test_configurations_complete(configurations, expected_configurations)

  test_no_error_files(configurations, summary)

  test_words_not_in_log_file(configurations, summary)

  test_results_correct(configurations, summary)


def test_configurations_complete(configurations, expected_configurations):
  name = 'Configurations complete'
  print_testcase(name)

  differences = copy.deepcopy(expected_configurations)
  passed = True
  for c in configurations:
    expected_count = expected_configurations[(c.number_of_nodes, c.fault_group)]
    difference = expected_count - len(c.repetitions) - len(c.invalid_repetitions)
    differences[(c.number_of_nodes, c.fault_group)] = difference

  for ((number_of_nodes, fault_group), difference) in differences.items():
    if difference:
      print("  Configurations %i %s is missing %i repetitions from %i" % (number_of_nodes, fault_group, difference, expected_configurations[(number_of_nodes, fault_group)]))
      passed = False

  print_testcase_end(name, passed)


def test_no_error_files(configurations, summary):
  name = 'Error file'
  print_testcase(name)

  passed = True
  for c in configurations:
    if len(c.invalid_repetitions) != 0:
      passed = False
      print("  Configuration %i %s has %i errors" % (c.number_of_nodes, c.fault_group, len(c.invalid_repetitions)))
    if not summary:
      for r in c.invalid_repetitions:
        print('    Repetition: %i' % r.number)
        for e in r.errors:
          print('      ' + e)
        print()
      if c.invalid_repetitions:
        print("---------\n")

  print_testcase_end(name, passed)


def test_words_not_in_log_file(configurations, summary):
  name = 'Clean logs'
  print_testcase(name)
  bad_words = ['error', 'exception']

  passed = True
  for c in configurations:
    first_error_for_configuration = True
    for r in c.repetitions + c.invalid_repetitions:
      first_error_for_repetition = True
      for l in r.get_log_file().readlines():
        lower_line = l.lower()
        for w in bad_words:
          if w in lower_line:
            if first_error_for_configuration:
              first_error_for_configuration = False
              print("  In configuration: %d %s" % (c.number_of_nodes, c.fault_group))
            if first_error_for_repetition:
              first_error_for_repetition = False
              print("    Repetition %d" % r.number)
            if not summary:
              print("      %s" % l)

  print_testcase_end(name, passed)


def test_results_correct(configurations, summary):
  name = 'Correct results'
  print_testcase(name)

  passed = True
  for c in configurations:
    first_error_for_configuration = True
    for r in c.repetitions + c.invalid_repetitions:
      for l in r.get_log_file().readlines():
        if 'result is correct' in l:
          break
      else:
        passed = False
        if first_error_for_configuration:
          first_error_for_configuration = False
          print("In configuration: %d %s" % (c.number_of_nodes, c.fault_group))
        print("    Repetition %i results are not correct" % r.number)

    print_testcase_end(name, passed)


def print_testcase(name):
  print('+' * len(name))
  print('+' * len(name))
  print(name)
  print('-' * len(name))


def print_testcase_end(name, passed):
  print('')
  print('Passed' if passed else 'Failed')
  print('-' * len(name))
  print('')


main()
