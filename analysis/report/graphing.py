import numpy as np
import scipy.stats as st
import plotly.graph_objs as go
import plotly


def get_scatter_graph_data(x_axis, values, name):
  return go.Scatter(
    x=x_axis,
    y=values,
    mode='markers',
    marker={
      'size': 15
    },
    name=name
  )


def get_scatter_graph_with_mean_and_confidence_interval(x_axis, values, name):
  mean = np.mean(values)
  # low_confidence, high_confidence = st.t.interval(0.95, len(values) - 1, loc=mean,
  #                                                 scale=st.sem(values))
  values_trace = go.Scatter(
    x=x_axis,
    y=values,
    mode='lines+markers',
    marker={
      'size': 15
    },
    name=name
  )

  mean_trace = go.Scatter(
    x=x_axis,
    y=[mean] * len(x_axis),
    mode='lines',
    name='mean'
  )
  #
  # low_confidence_trace = go.Scatter(
  #     x=x_axis,
  #     y=[low_confidence] * len(x_axis),
  #     mode='lines',
  #     name='95'
  # )
  #
  # high_confidence_trace = go.Scatter(
  #     x=x_axis,
  #     y=[high_confidence] * len(x_axis),
  #     mode='lines',
  #     name='95'
  # )

  # data = [values_trace, low_confidence_trace, mean_trace, high_confidence_trace]
  data = [values_trace, mean_trace]
  return data


def get_box_trace(values, name, color='rgb(107,174,214)'):
  return go.Box(
    y=values,
    name=name,
    boxpoints='outliers',
    boxmean=True,
    marker=dict(
      color=color),
    line=dict(
      color=color)
  )

def hide_layout(data):
  layout = go.Layout(showlegend=False)
  figure = go.Figure(data=data, layout=layout)
  return figure
