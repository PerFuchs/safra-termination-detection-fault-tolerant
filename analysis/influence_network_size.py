import operator
import statistics
from collections import defaultdict, OrderedDict

import plotly
from plotly import graph_objs as go

import graphing
from utils import difference_in_percent, present_linear_relationship


def analyse_influence_of_network_size(configurations):
  grouped_by_fault_group = defaultdict(lambda: list())
  for c in configurations:
    grouped_by_fault_group[c.fault_group].append(c)
  grouped_by_fault_group_sorted = dict(
    map(lambda i: (i[0], sorted(i[1], key=lambda c: c.number_of_nodes)), grouped_by_fault_group.items()))
  grouped_by_fault_group_sorted = OrderedDict(
    sorted(grouped_by_fault_group_sorted.items(), key=lambda i: i[0]))

  present_token_and_token_after_termination(grouped_by_fault_group_sorted)


def present_token_and_token_after_termination(configurations):
  data = []

  network_sizes = 5
  for i in range(network_sizes):
    fs_configuration = configurations['0 fs'][i]
    ft_configuration = configurations['0'][i]
    data.append(graphing.get_box_trace(fs_configuration.get_tokens(), 'FS %i' % fs_configuration.number_of_nodes))
    data.append(graphing.get_box_trace(fs_configuration.get_tokens_after_termination(),
                                       'FS %i' % fs_configuration.number_of_nodes, 'rgb(255,140,0)'))

    data.append(graphing.get_box_trace(ft_configuration.get_tokens(), 'FT %i' % ft_configuration.number_of_nodes))
    data.append(graphing.get_box_trace(ft_configuration.get_tokens_after_termination(),
                                       'FT %i' % ft_configuration.number_of_nodes, 'rgb(255,140,0'))

  plotly.offline.plot(graphing.hide_layout(data), filename='../graphs/tokens_and_token_after_termination_box_plot.html')
