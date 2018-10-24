import csv
import operator
import os
from collections import defaultdict

from os import listdir

from os.path import isdir, basename, isfile

USE_REANALYSING_RESULTS = True


class MyDialect(csv.excel):
  delimiter = ';'


class Repetition:
  REANALYSIS_FOLDER = 'reanalyse'

  def read_warning_file(self):
    warnings_file = '/'.join([self.folder, '.warn'])
    reanalysis_warnings_file = '/'.join([self.folder, Repetition.REANALYSIS_FOLDER, '.warn'])

    if isfile(warnings_file):
      self.warnings = open(warnings_file).readlines()
    if isfile(reanalysis_warnings_file):
      self.reanalysis_warnings = open(reanalysis_warnings_file).readlines()

  def read_error_file(self):
    errors_file = '/'.join([self.folder, '.error'])
    reanalysis_errors_file = '/'.join([self.folder, Repetition.REANALYSIS_FOLDER, '.error'])

    if isfile(errors_file):
      self.errors = open(errors_file).readlines()
    if isfile(reanalysis_errors_file):
      self.reanalysis_errors = open(reanalysis_errors_file).readlines()

  def __init__(self, folder, number_of_nodes, fault_percentage):
    self.folder = folder
    self.is_reanalyzed = isdir('/'.join((folder, Repetition.REANALYSIS_FOLDER))) and USE_REANALYSING_RESULTS

    self.number = int(basename(self.folder))

    self.errors = []
    self.reanalysis_errors = []

    self.read_error_file()

    self.warnings = []
    self.reanalysis_warnings = []
    self.read_warning_file()

    try:
      with open('/'.join(
          (folder, Repetition.REANALYSIS_FOLDER if self.is_reanalyzed else '', 'safraStatistics.csv'))) as csvFile:
        reader = csv.DictReader(csvFile, dialect=MyDialect())
        for statistics in reader:
          self.tokens = int(statistics['tokens'])
          self.tokens_after_termination = int(statistics['tokenAfterTermination'])
          self.backup_tokens = int(statistics['backupToken'])
          self.token_bytes = int(statistics['tokenSize (bytes)']) / self.tokens
          self.safra_time = float(statistics['safraTime (seconds)'])
          self.safra_time_after_termination = float(statistics['safraTimeAfterTermination'])
          self.total_time = float(statistics['totalTime'])
          self.basic_time = float(statistics['basicTime'])

          if 'totalTimeAfterTermination' in statistics:
            self.total_time_after_termination = float(statistics['totalTimeAfterTermination'])
          else:
            self.total_time_after_termination = -1

          if 'numberOfNodesCrashed' in statistics:
            self.number_of_nodes_crashed = int(statistics['numberOfNodesCrashed'])
            real_fault_percentage = self.number_of_nodes_crashed / number_of_nodes
            if real_fault_percentage < fault_percentage - 0.1:
              self.valid = False
              self.errors.append(
                'Fault percentage of %f but aimed for %f' % (real_fault_percentage, fault_percentage))
            if self.number_of_nodes_crashed == 0 and fault_percentage > 0:
              self.valid = False
              self.errors.append('No faults but aimed for 1 to 5.')
          break  # There should be only one line
    except FileNotFoundError:
      self.errors.append('Missing statistics')

    logs = len(list(filter(lambda f: f.endswith('.log'), listdir(folder))))
    chandy_misra_results = len(list(filter(lambda f: f.endswith('.chandyMisra'), listdir(folder))))
    afek_kutten_yung_results = len(list(filter(lambda f: f.endswith('.afekKuttenYung'), listdir(folder))))

    if logs != number_of_nodes + 1:  # 1 is the out.log file summarizing the whole run
      self.errors.append('Missing some node logs')
    if not (chandy_misra_results == number_of_nodes or afek_kutten_yung_results == number_of_nodes):
      self.errors.append('Missing results')
    if not os.path.isfile('%s/out.log' % self.folder):
      self.errors.append('Missing general log file')

    self.valid = len(self.reanalysis_errors) == 0 if self.is_reanalyzed else len(self.errors) == 0

  def print_warnings(self):
    if self.warnings:
      print('Analysis warnings:')
      print('  ', end='')
      print('\n  '.join(self.warnings))

    if self.reanalysis_warnings:
      print('Reanalysis errors: ')
      print('  ', end='')
      print('\n  '.join(self.reanalysis_warnings))

  def print_errors(self):
    if self.errors:
      print("Analysis errors:")
      print('  ', end='')
      print('\n  '.join(self.errors))

    if self.reanalysis_errors:
      print('Reanalysis errors: ')
      print('  ', end='')
      print('\n  '.join(self.reanalysis_errors))

  def print_errors_and_warnings(self):
    self.print_errors()
    self.print_warnings()

  def get_log_file(self):
    return open('%s/out.log' % self.folder)


class Configuration:

  def __init__(self, repetitions, invalid_repetitions, number_of_nodes, fault_percentage, fault_sensitive, fault_group,
               expected_repetitions):
    self.fault_group = fault_group
    self.fault_sensitive = fault_sensitive
    self.fault_percentage = fault_percentage
    self.number_of_nodes = number_of_nodes
    self.invalid_repetitions = invalid_repetitions
    self.repetitions = repetitions
    self.expected_repetitions = expected_repetitions

  @classmethod
  def from_folder(cls, folder):
    configuration_name = basename(folder)
    number_of_nodes, fault_percentage, expected_repetitions = configuration_name.split('-')
    number_of_nodes = int(number_of_nodes)
    if fault_percentage == 'fs':
      fault_sensitive = True
      fault_percentage = 0.0
    else:
      fault_sensitive = False
      fault_percentage = float(fault_percentage)

    if fault_sensitive:
      fault_group = '0 fs'
    elif fault_percentage == 0.0:
      fault_group = '0'
    elif fault_percentage == 0.9:
      fault_group = '90'
    else:
      fault_group = '5n'

    expected_repetitions = expected_repetitions.replace('.run', '')
    if '_' in expected_repetitions:
      expected_repetitions, _ = expected_repetitions.split('_')
    expected_repetitions = int(expected_repetitions)

    repetitions = []
    invalid_repetitions = []
    for file_name in listdir(folder):
      if isdir('/'.join((folder, file_name))) and not file_name.endswith('.failure'):
        r = Repetition('/'.join((folder, file_name)), number_of_nodes, fault_percentage)
        if r.valid:
          repetitions.append(r)
        else:
          invalid_repetitions.append(r)
    return Configuration(repetitions, invalid_repetitions, number_of_nodes, fault_percentage, fault_sensitive,
                         fault_group, expected_repetitions)

  def get_tokens(self):
    return list(map(lambda r: r.tokens, self.repetitions))

  def get_token_bytes(self):
    return list(map(lambda r: r.token_bytes, self.repetitions))

  def get_safra_times(self):
    return list(map(lambda r: r.safra_time, self.repetitions))

  def get_total_times(self):
    return list(map(lambda r: r.total_time, self.repetitions))

  def get_average_total_times(self):
    return list(map(operator.truediv, self.get_total_times(), [self.number_of_nodes] * len(self.repetitions)))

  def get_safra_times_after_termination(self):
    return list(map(lambda r: r.safra_time_after_termination, self.repetitions))

  def get_total_times_after_termination(self):
    return list(map(lambda r: r.total_time_after_termination, self.repetitions))

  def get_number_of_nodes_crashed(self):
    return list(map(lambda r: r.number_of_nodes_crashed, self.repetitions))

  def get_backup_tokens(self):
    return list(map(lambda r: r.backup_tokens, self.repetitions))

  def get_tokens_after_termination(self):
    return list(map(lambda r: r.tokens_after_termination, self.repetitions))

  def get_basic_times(self):
    return list(map(lambda r: r.basic_time, self.repetitions))

  def merge_with(self, other):
    assert self.fault_sensitive == other.fault_sensitive
    assert self.number_of_nodes == other.number_of_nodes
    assert self.fault_percentage == other.fault_percentage
    self.repetitions += other.repetitions
    self.invalid_repetitions += other.invalid_repetitions
    self.expected_repetitions += other.expected_repetitions
    return self


def get_configurations(folder):
  configurations = defaultdict(lambda: list())

  for file_name in listdir(folder):
    configuration_folder = '/'.join((folder, file_name))
    if isdir(configuration_folder) and configuration_folder.endswith('.run'):
      configuration = Configuration.from_folder(configuration_folder)
      configurations[(configuration.number_of_nodes, configuration.fault_percentage, configuration.fault_sensitive,
                      configuration.fault_group)].append(configuration)

  merged_configurations = []
  for key_values, similiar_configurations in configurations.items():
    merged = Configuration([], [], *key_values, expected_repetitions=0)
    for c in similiar_configurations:
      merged = merged.merge_with(c)
    merged_configurations.append(merged)

  merged_configurations = sorted(merged_configurations, key=lambda c: c.fault_sensitive)
  merged_configurations = sorted(merged_configurations, key=lambda c: c.fault_percentage)
  merged_configurations = sorted(merged_configurations, key=lambda c: c.number_of_nodes)

  return merged_configurations
