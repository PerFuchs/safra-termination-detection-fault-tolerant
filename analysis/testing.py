import sys

from read_results import get_configurations


def main():
  experiment_folder = sys.argv[1]

  summary = sys.argv[2] == "summary"

  configurations = get_configurations(experiment_folder)

  test_configurations_complete(configurations)

  test_no_error_files(configurations, summary)

  test_words_not_in_log_file(configurations, summary)

  test_results_correct(configurations, summary)


def test_configurations_complete(configurations):
  name = 'Configurations complete'
  print_testcase(name)

  passed = True
  for c in configurations:
    total_repetition_count = len(c.repetitions) + len(c.invalid_repetitions)
    if total_repetition_count != c.expected_repetitions:
      print("  Configurations %i %s is missing %i repetitions from %i" % (c.number_of_nodes, c.fault_group, c.expected_repetitions - total_repetition_count , c.expected_repetitions))
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
        for e in categorize_errors(r.errors):
          print('      ' + e)
        print()
      if c.invalid_repetitions:
        print("---------\n")

  print_testcase_end(name, passed)


def categorize_errors(errors):
  unified_errors = []

  termination_detected_to_early_encountered = False
  termination_not_detected_encountered = False
  for e in errors:
    if '<<TerminationDetectedTooEarly>>' in e:
      if not termination_detected_to_early_encountered:
        termination_detected_to_early_encountered = True
        unified_errors.append(e)
    elif 'Termination wasn\'t detected in' in e:
      if not termination_not_detected_encountered:
        termination_not_detected_encountered = True
        unified_errors.append(e)
    else:
      unified_errors.append(e)

  return unified_errors



def contains_either(string, words):
  return any(map(lambda w: w in string, words))


def contains_none(string, words):
  return all(map(lambda w: w not in string, words))


def test_words_not_in_log_file(configurations, summary):
  name = 'Clean logs'
  print_testcase(name)
  bad_words = ['error', 'exception']
  exclude = ['terminationdetectedtooearly', 'crashdetectedaftertermination', 'termination was detected to early']

  passed = True
  for c in configurations:
    first_error_for_configuration = True
    for r in c.repetitions + c.invalid_repetitions:
      first_error_for_repetition = True
      for l in r.get_log_file().readlines():
        lower_line = l.lower()

        if contains_either(lower_line, bad_words) and contains_none(lower_line, exclude):
          if first_error_for_configuration:
            first_error_for_configuration = False
            print("  In configuration: %d %s" % (c.number_of_nodes, c.fault_group))
          if first_error_for_repetition:
            printed_already = set()
            first_error_for_repetition = False
            print("    Repetition %d" % r.number)
          if not summary and l not in printed_already:
            print("      %s" % l)
            printed_already.add(l)

  print_testcase_end(name, passed)


def test_results_correct(configurations, summary):
  name = 'Correct results'
  print_testcase(name)

  passed = True
  for c in configurations:
    first_error_for_configuration = True
    for r in c.repetitions:
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
